package com.example.sharkmentor.ui.screens.notes.drawing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RecognizedShape(
    val shapeKind: ShapeKind,
    val points: List<DrawingPoint>,
    val labelTr: String
)

/**
 * Pure-JVM geometric recognizer that straightens freehand pen strokes into clean
 * lines, triangles, rectangles/squares, and circles/ellipses while leaving regular
 * handwriting untouched.
 */
object AutoShapeRecognizer {

    private const val MIN_POINTS = 6
    private const val MIN_DIAGONAL_PX = 66f
    private const val MIN_PATH_LENGTH_PX = 70f
    private const val MIN_LINE_CHORD_PX = 80f
    private const val ANGLE_SNAP_DEG = 6.5

    fun recognize(rawPoints: List<DrawingPoint>): RecognizedShape? {
        if (rawPoints.size < MIN_POINTS) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var totalLength = 0f

        for (i in rawPoints.indices) {
            val p = rawPoints[i]
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
            if (i > 0) {
                totalLength += rawPoints[i - 1].distanceTo(p)
            }
        }

        val width = maxX - minX
        val height = maxY - minY
        val diagonal = hypot(width, height)

        if (diagonal < MIN_DIAGONAL_PX || totalLength < MIN_PATH_LENGTH_PX) {
            return null
        }

        // 1. Straight Line Check
        tryRecognizeLine(rawPoints, totalLength)?.let { return it }

        // 1.5 Arrow Check (straight shaft + short angled tip at the end)
        tryRecognizeArrow(rawPoints, totalLength)?.let { return it }

        // 2. Closed-Shape Gate (Circle/Ellipse, Triangle, Rectangle/Square)
        // Trim natural start/end overshoot tails on hand-drawn loops ("O" drawn slightly past start)
        val loopPoints = trimClosedLoopOvershoot(rawPoints, diagonal)
        val resampled = resampleUniform(loopPoints, stepPx = (diagonal / 42f).coerceIn(5f, 12f))
        if (resampled.size < 10) return null

        var cMinX = Float.MAX_VALUE
        var cMinY = Float.MAX_VALUE
        var cMaxX = -Float.MAX_VALUE
        var cMaxY = -Float.MAX_VALUE
        var loopLength = 0f
        for (i in resampled.indices) {
            val p = resampled[i]
            if (p.x < cMinX) cMinX = p.x
            if (p.y < cMinY) cMinY = p.y
            if (p.x > cMaxX) cMaxX = p.x
            if (p.y > cMaxY) cMaxY = p.y
            if (i > 0) loopLength += resampled[i - 1].distanceTo(p)
        }
        val cWidth = cMaxX - cMinX
        val cHeight = cMaxY - cMinY
        val cDiagonal = hypot(cWidth, cHeight)

        val closeDist = resampled.first().distanceTo(resampled.last())
        val maxCloseGap = max(46f, cDiagonal * 0.34f)

        if (closeDist > maxCloseGap) return null
        if (cWidth < 44f || cHeight < 44f) return null

        val aspectRatio = max(cWidth, cHeight) / min(cWidth, cHeight).coerceAtLeast(1f)
        if (aspectRatio > 3.2f) return null

        // Guard against multi-loop scribbles or spirals
        val boxPerimeter = 2f * (cWidth + cHeight)
        if (loopLength > boxPerimeter * 1.55f || loopLength < cDiagonal * 1.85f) {
            return null
        }

        val ellipseCandidate = evaluateEllipse(resampled, cMinX, cMinY, cMaxX, cMaxY)
        val polygonCandidate = evaluatePolygon(resampled, cMinX, cMinY, cMaxX, cMaxY, cDiagonal)

        return when {
            polygonCandidate != null && ellipseCandidate != null -> {
                if (polygonCandidate.second <= ellipseCandidate.second * 1.12f) {
                    polygonCandidate.first
                } else {
                    ellipseCandidate.first
                }
            }
            polygonCandidate != null -> polygonCandidate.first
            ellipseCandidate != null -> ellipseCandidate.first
            else -> null
        }
    }

    /**
     * Trims small entry/exit overshoot tails on hand-drawn closed shapes (e.g. a circle "O"
     * whose pen passes slightly beyond its starting point) so closure and ellipse fitting stay accurate.
     */
    private fun trimClosedLoopOvershoot(
        points: List<DrawingPoint>,
        diagonal: Float
    ): List<DrawingPoint> {
        if (points.size < 14) return points
        val directGap = points.first().distanceTo(points.last())
        if (directGap <= max(28f, diagonal * 0.16f)) return points

        val startLimit = (points.size * 0.22f).toInt().coerceAtLeast(1)
        val endStart = (points.size * 0.72f).toInt().coerceAtMost(points.lastIndex - 1)
        if (endStart <= startLimit + 4) return points

        var bestDist = directGap
        var bestI = 0
        var bestJ = points.lastIndex
        for (i in 0..startLimit) {
            val pi = points[i]
            for (j in endStart..points.lastIndex) {
                val d = pi.distanceTo(points[j])
                if (d < bestDist) {
                    bestDist = d
                    bestI = i
                    bestJ = j
                }
            }
        }
        return if (bestDist < directGap * 0.65f && (bestJ - bestI) >= (points.size * 0.58f).toInt()) {
            points.subList(bestI, bestJ + 1)
        } else {
            points
        }
    }

    /**
     * Resamples a polyline to uniform spacing so fast sparse strokes have full angular sector coverage.
     */
    private fun resampleUniform(points: List<DrawingPoint>, stepPx: Float): List<DrawingPoint> {
        if (points.size < 2 || stepPx <= 0.5f) return points
        val out = ArrayList<DrawingPoint>(points.size * 2)
        out.add(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dist = a.distanceTo(b)
            if (dist < 0.001f) continue
            val steps = (dist / stepPx).toInt()
            for (s in 1..steps) {
                val t = (s * stepPx / dist).coerceAtMost(1f)
                out.add(
                    DrawingPoint(
                        x = a.x + (b.x - a.x) * t,
                        y = a.y + (b.y - a.y) * t,
                        pressure = a.pressure + (b.pressure - a.pressure) * t
                    )
                )
            }
            if (out.last().distanceTo(b) > 0.5f) {
                out.add(b)
            }
        }
        return out
    }

    private fun tryRecognizeLine(
        points: List<DrawingPoint>,
        totalLength: Float
    ): RecognizedShape? {
        val start = points.first()
        val end = points.last()
        val chord = start.distanceTo(end)
        if (chord < MIN_LINE_CHORD_PX) return null

        // Path length must be very close to direct chord distance (rules out arcs/letters/loops)
        if ((totalLength / chord) > 1.11f) return null

        var maxPerp = 0f
        var sumSqPerp = 0.0
        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistanceToLine(points[i], start, end)
            if (d > maxPerp) maxPerp = d
            sumSqPerp += (d * d).toDouble()
        }
        val rmsPerp = sqrt(sumSqPerp / (points.size - 2).coerceAtLeast(1)).toFloat()

        val maxAllowed = max(11f, chord * 0.075f)
        val rmsAllowed = max(5.5f, chord * 0.042f)
        if (maxPerp > maxAllowed || rmsPerp > rmsAllowed) return null

        val snappedEnd = snapEndpointAngle(start, end)
        return RecognizedShape(
            shapeKind = ShapeKind.LINE,
            points = listOf(
                DrawingPoint(start.x, start.y, 1.0f),
                DrawingPoint(snappedEnd.x, snappedEnd.y, 1.0f)
            ),
            labelTr = "Düz Çizgi"
        )
    }

    private fun snapEndpointAngle(start: DrawingPoint, end: DrawingPoint): DrawingPoint {
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        val deg = abs(atan2(dy, dx) * 180.0 / PI)
        return when {
            deg <= ANGLE_SNAP_DEG || abs(deg - 180.0) <= ANGLE_SNAP_DEG ->
                DrawingPoint(end.x, start.y, 1.0f)
            abs(deg - 90.0) <= ANGLE_SNAP_DEG ->
                DrawingPoint(start.x, end.y, 1.0f)
            else -> DrawingPoint(end.x, end.y, 1.0f)
        }
    }

    private fun evaluateEllipse(
        points: List<DrawingPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ): Pair<RecognizedShape, Float>? {
        val w = maxX - minX
        val h = maxY - minY
        val rx = (w * 0.5f).coerceAtLeast(1f)
        val ry = (h * 0.5f).coerceAtLeast(1f)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f

        val sectorVisited = BooleanArray(12)
        var sumAbsErr = 0f
        var sumSqErr = 0.0
        var maxErr = 0f

        for (p in points) {
            val nx = (p.x - cx) / rx
            val ny = (p.y - cy) / ry
            val r = hypot(nx, ny)
            val err = abs(r - 1.0f)
            sumAbsErr += err
            sumSqErr += (err * err).toDouble()
            if (err > maxErr) maxErr = err

            val angle = atan2(ny.toDouble(), nx.toDouble())
            val normAngle = if (angle < 0) angle + 2.0 * PI else angle
            val sector = ((normAngle / (2.0 * PI)) * 12.0).toInt().coerceIn(0, 11)
            sectorVisited[sector] = true
        }

        val coveredSectors = sectorVisited.count { it }
        if (coveredSectors < 10) return null

        val meanErr = sumAbsErr / points.size.toFloat()
        val rmsErr = sqrt(sumSqErr / points.size.toDouble()).toFloat()

        if (meanErr > 0.175f || maxErr > 0.40f) return null

        val ratio = w / h
        val isCircle = ratio in 0.84f..1.20f
        val (outMinX, outMinY, outMaxX, outMaxY) = if (isCircle) {
            val r = (rx + ry) * 0.5f
            listOf(cx - r, cy - r, cx + r, cy + r)
        } else {
            listOf(minX, minY, maxX, maxY)
        }

        val shape = RecognizedShape(
            shapeKind = ShapeKind.ELLIPSE,
            points = listOf(
                DrawingPoint(outMinX, outMinY, 1.0f),
                DrawingPoint(outMaxX, outMaxY, 1.0f)
            ),
            labelTr = if (isCircle) "Daire" else "Elips"
        )
        return shape to rmsErr
    }

    private fun evaluatePolygon(
        points: List<DrawingPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        diagonal: Float
    ): Pair<RecognizedShape, Float>? {
        val closedPoints = points + points.first()
        val eps = max(11f, diagonal * 0.068f)
        val rawSimplified = simplifyClosedPolygon(closedPoints, eps)
        val vertices = mergeCollinearAndCloseVertices(
            vertices = rawSimplified,
            minEdgeLen = max(18f, diagonal * 0.16f),
            minTurnDeg = 24.0
        )

        if (vertices.size == 3) {
            return evaluateTriangle(points, vertices, minX, minY, maxX, maxY, diagonal)
        }
        if (vertices.size == 4) {
            return evaluateRectangle(points, vertices, minX, minY, maxX, maxY, diagonal)
        }
        return null
    }

    private fun evaluateTriangle(
        points: List<DrawingPoint>,
        vertices: List<DrawingPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        diagonal: Float
    ): Pair<RecognizedShape, Float>? {
        val a = vertices[0]
        val b = vertices[1]
        val c = vertices[2]

        val minEdge = diagonal * 0.22f
        if (a.distanceTo(b) < minEdge || b.distanceTo(c) < minEdge || c.distanceTo(a) < minEdge) {
            return null
        }

        // Check non-degenerate area
        val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) * 0.5f
        val boxArea = ((maxX - minX) * (maxY - minY)).coerceAtLeast(1f)
        if (area < boxArea * 0.14f) return null

        val (rmsDist, maxDist) = polygonFitError(points, vertices)
        if (maxDist > max(16f, diagonal * 0.115f) || rmsDist > max(9f, diagonal * 0.068f)) {
            return null
        }

        val snapped = snapTriangleVertices(vertices)
        val normalizedRms = (rmsDist / (diagonal * 0.5f)).coerceAtLeast(0.001f)
        return RecognizedShape(
            shapeKind = ShapeKind.TRIANGLE,
            points = snapped,
            labelTr = "Üçgen"
        ) to normalizedRms
    }

    private fun snapTriangleVertices(vertices: List<DrawingPoint>): List<DrawingPoint> {
        val pts = vertices.map { DrawingPoint(it.x, it.y, 1.0f) }.toMutableList()
        // Gently level nearly-horizontal or nearly-vertical sides for crisp geometry
        for (i in 0 until 3) {
            val j = (i + 1) % 3
            val p1 = pts[i]
            val p2 = pts[j]
            val dx = (p2.x - p1.x).toDouble()
            val dy = (p2.y - p1.y).toDouble()
            val deg = abs(atan2(dy, dx) * 180.0 / PI)
            if (deg <= 5.5 || abs(deg - 180.0) <= 5.5) {
                val avgY = (p1.y + p2.y) * 0.5f
                pts[i] = p1.copy(y = avgY)
                pts[j] = p2.copy(y = avgY)
            } else if (abs(deg - 90.0) <= 5.5) {
                val avgX = (p1.x + p2.x) * 0.5f
                pts[i] = p1.copy(x = avgX)
                pts[j] = p2.copy(x = avgX)
            }
        }
        return pts
    }

    private fun evaluateRectangle(
        points: List<DrawingPoint>,
        vertices: List<DrawingPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        diagonal: Float
    ): Pair<RecognizedShape, Float>? {
        val minEdge = diagonal * 0.18f
        for (i in 0 until 4) {
            val prev = vertices[(i + 3) % 4]
            val curr = vertices[i]
            val next = vertices[(i + 1) % 4]
            if (curr.distanceTo(next) < minEdge) return null

            val angleDeg = interiorAngleDeg(prev, curr, next)
            if (angleDeg !in 62.0..118.0) return null
        }

        val (rmsDist, maxDist) = polygonFitError(points, vertices)
        if (maxDist > max(16f, diagonal * 0.115f) || rmsDist > max(9f, diagonal * 0.068f)) {
            return null
        }

        val w = maxX - minX
        val h = maxY - minY
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val ratio = w / h.coerceAtLeast(1f)
        val isSquare = ratio in 0.85f..1.18f
        val (outMinX, outMinY, outMaxX, outMaxY) = if (isSquare) {
            val half = (w + h) * 0.25f
            listOf(cx - half, cy - half, cx + half, cy + half)
        } else {
            listOf(minX, minY, maxX, maxY)
        }

        val normalizedRms = (rmsDist / (diagonal * 0.5f)).coerceAtLeast(0.001f)
        return RecognizedShape(
            shapeKind = ShapeKind.RECTANGLE,
            points = listOf(
                DrawingPoint(outMinX, outMinY, 1.0f),
                DrawingPoint(outMaxX, outMaxY, 1.0f)
            ),
            labelTr = if (isSquare) "Kare" else "Dikdörtgen"
        ) to normalizedRms
    }

    private fun polygonFitError(
        points: List<DrawingPoint>,
        vertices: List<DrawingPoint>
    ): Pair<Float, Float> {
        var maxDist = 0f
        var sumSq = 0.0
        for (p in points) {
            var best = Float.MAX_VALUE
            for (i in vertices.indices) {
                val a = vertices[i]
                val b = vertices[(i + 1) % vertices.size]
                val d = distancePointToSegment(p, a, b)
                if (d < best) best = d
            }
            if (best > maxDist) maxDist = best
            sumSq += (best * best).toDouble()
        }
        val rms = sqrt(sumSq / points.size.coerceAtLeast(1)).toFloat()
        return rms to maxDist
    }

    private fun simplifyClosedPolygon(
        closedPoints: List<DrawingPoint>,
        epsilon: Float
    ): List<DrawingPoint> {
        if (closedPoints.size <= 4) return closedPoints.dropLast(1)
        // Pick start vertex farthest from centroid so split lands on an actual corner
        var cx = 0f
        var cy = 0f
        val openCount = closedPoints.size - 1
        for (i in 0 until openCount) {
            cx += closedPoints[i].x
            cy += closedPoints[i].y
        }
        cx /= openCount.coerceAtLeast(1)
        cy /= openCount.coerceAtLeast(1)
        val center = DrawingPoint(cx, cy)

        var startIdx = 0
        var maxDist = -1f
        for (i in 0 until openCount) {
            val d = closedPoints[i].distanceTo(center)
            if (d > maxDist) {
                maxDist = d
                startIdx = i
            }
        }

        val rotated = ArrayList<DrawingPoint>(openCount + 1)
        for (k in 0 until openCount) {
            rotated.add(closedPoints[(startIdx + k) % openCount])
        }
        rotated.add(rotated.first())

        // Split at farthest point from rotated[0] before running Douglas-Peucker on both halves
        var splitIdx = 1
        var farthest = -1f
        for (i in 1 until openCount) {
            val d = rotated[i].distanceTo(rotated[0])
            if (d > farthest) {
                farthest = d
                splitIdx = i
            }
        }

        val half1 = douglasPeucker(rotated.subList(0, splitIdx + 1), epsilon)
        val half2 = douglasPeucker(rotated.subList(splitIdx, rotated.size), epsilon)
        return half1.dropLast(1) + half2.dropLast(1)
    }

    private fun mergeCollinearAndCloseVertices(
        vertices: List<DrawingPoint>,
        minEdgeLen: Float,
        minTurnDeg: Double
    ): List<DrawingPoint> {
        if (vertices.size <= 3) return vertices
        val list = vertices.toMutableList()
        var changed = true
        while (changed && list.size > 3) {
            changed = false
            // Merge adjacent vertices closer than minEdgeLen
            var i = 0
            while (i < list.size && list.size > 3) {
                val nextIdx = (i + 1) % list.size
                if (list[i].distanceTo(list[nextIdx]) < minEdgeLen) {
                    val mid = DrawingPoint(
                        x = (list[i].x + list[nextIdx].x) * 0.5f,
                        y = (list[i].y + list[nextIdx].y) * 0.5f,
                        pressure = 1.0f
                    )
                    list[i] = mid
                    list.removeAt(nextIdx)
                    changed = true
                    break
                }
                i++
            }
            if (changed) continue

            // Remove nearly-collinear vertices
            var j = 0
            while (j < list.size && list.size > 3) {
                val prev = list[(j + list.size - 1) % list.size]
                val curr = list[j]
                val next = list[(j + 1) % list.size]
                val interior = interiorAngleDeg(prev, curr, next)
                val turn = 180.0 - interior
                if (turn < minTurnDeg) {
                    list.removeAt(j)
                    changed = true
                    break
                }
                j++
            }
        }
        return list
    }

    private fun interiorAngleDeg(
        prev: DrawingPoint,
        curr: DrawingPoint,
        next: DrawingPoint
    ): Double {
        val v1x = (prev.x - curr.x).toDouble()
        val v1y = (prev.y - curr.y).toDouble()
        val v2x = (next.x - curr.x).toDouble()
        val v2y = (next.y - curr.y).toDouble()
        val len1 = hypot(v1x, v1y)
        val len2 = hypot(v2x, v2y)
        if (len1 < 1e-4 || len2 < 1e-4) return 180.0
        val cosTheta = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1.0, 1.0)
        return acos(cosTheta) * 180.0 / PI
    }

    private fun douglasPeucker(
        points: List<DrawingPoint>,
        epsilon: Float
    ): List<DrawingPoint> {
        if (points.size <= 2) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistanceToLine(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, index + 1), epsilon)
            val right = douglasPeucker(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistanceToLine(
        point: DrawingPoint,
        lineStart: DrawingPoint,
        lineEnd: DrawingPoint
    ): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) return point.distanceTo(lineStart)
        val cross = abs(dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        return cross / hypot(dx, dy)
    }

    private fun distancePointToSegment(
        point: DrawingPoint,
        a: DrawingPoint,
        b: DrawingPoint
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) return point.distanceTo(a)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(point.x - projX, point.y - projY)
    }

    private fun tryRecognizeArrow(
        points: List<DrawingPoint>,
        totalLength: Float
    ): RecognizedShape? {
        if (points.size < 10) return null
        val start = points.first()
        // Find the point farthest from start -> likely the arrow tip!
        var tipIdx = 0
        var maxDist = 0f
        for (i in 1 until points.size) {
            val d = start.distanceTo(points[i])
            if (d > maxDist) {
                maxDist = d
                tipIdx = i
            }
        }
        // Arrow tip should be past 55% of the stroke and leave a small arrowhead tail after it
        if (tipIdx < (points.size * 0.55f).toInt() || tipIdx >= points.lastIndex - 1) return null
        if (maxDist < MIN_LINE_CHORD_PX) return null

        val tip = points[tipIdx]
        var shaftLen = 0f
        for (i in 1..tipIdx) shaftLen += points[i - 1].distanceTo(points[i])
        if (shaftLen / maxDist > 1.16f) return null

        val headLen = (totalLength - shaftLen).coerceAtLeast(0f)
        if (headLen < 16f || headLen > maxDist * 0.65f) return null

        val snappedTip = snapEndpointAngle(start, tip)
        return RecognizedShape(
            shapeKind = ShapeKind.ARROW,
            points = listOf(
                DrawingPoint(start.x, start.y, 1.0f),
                DrawingPoint(snappedTip.x, snappedTip.y, 1.0f)
            ),
            labelTr = "Ok"
        )
    }

    /**
     * Live-resizes or extends a locked recognized shape while the user keeps dragging
     * without lifting the stylus/finger after hold-to-snap.
     */
    fun resizeRecognizedShape(
        baseShape: RecognizedShape,
        anchorX: Float,
        anchorY: Float,
        currentX: Float,
        currentY: Float
    ): RecognizedShape {
        val pts = baseShape.points
        if (pts.size < 2) return baseShape
        return when (baseShape.shapeKind) {
            ShapeKind.LINE, ShapeKind.ARROW -> {
                val start = pts.first()
                val rawEnd = DrawingPoint(currentX, currentY, 1.0f)
                val snappedEnd = snapEndpointAngle(start, rawEnd)
                baseShape.copy(points = listOf(start, snappedEnd))
            }
            else -> {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (p in pts) {
                    if (p.x < minX) minX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.x > maxX) maxX = p.x
                    if (p.y > maxY) maxY = p.y
                }
                val cx = (minX + maxX) * 0.5f
                val cy = (minY + maxY) * 0.5f
                val baseDist = hypot(anchorX - cx, anchorY - cy).coerceAtLeast(24f)
                val curDist = hypot(currentX - cx, currentY - cy).coerceAtLeast(16f)
                val scale = (curDist / baseDist).coerceIn(0.28f, 3.5f)
                val scaled = pts.map { p ->
                    DrawingPoint(
                        x = cx + (p.x - cx) * scale,
                        y = cy + (p.y - cy) * scale,
                        pressure = p.pressure
                    )
                }
                baseShape.copy(points = scaled)
            }
        }
    }
}
