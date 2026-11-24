package com.example.sharkmentor.core.theme

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class SolarTimes(
    val sunriseMinuteOfDay: Int,
    val sunsetMinuteOfDay: Int
) {
    fun formatSunrise(): String = formatMinuteOfDay(sunriseMinuteOfDay)
    fun formatSunset(): String = formatMinuteOfDay(sunsetMinuteOfDay)
}

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val normalized = ((minuteOfDay % 1440) + 1440) % 1440
    val h = normalized / 60
    val m = normalized % 60
    return "%02d:%02d".format(h, m)
}

/**
 * Pure JVM-testable night-window check supporting both overnight spans (e.g. 20:00 -> 07:00)
 * and same-day spans (e.g. 01:00 -> 06:00).
 */
fun isMinuteInNightWindow(
    currentMinuteOfDay: Int,
    nightStartMinute: Int,
    nightEndMinute: Int
): Boolean {
    val cur = ((currentMinuteOfDay % 1440) + 1440) % 1440
    val start = ((nightStartMinute % 1440) + 1440) % 1440
    val end = ((nightEndMinute % 1440) + 1440) % 1440
    if (start == end) return false
    return if (start > end) {
        // Overnight window (e.g., 20:00..23:59 or 00:00..06:59)
        cur >= start || cur < end
    } else {
        cur in start until end
    }
}

/**
 * Pure JVM-testable NOAA solar sunrise/sunset calculator from `(latitude, longitude, date, zoneId)`.
 * Zero network calls required — works offline from cached weather coordinates.
 */
object SolarScheduleCalculator {

    fun calculateSunriseSunset(
        latitude: Double,
        longitude: Double,
        date: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SolarTimes {
        val safeLat = latitude.coerceIn(-65.0, 65.0)
        val safeLon = longitude.coerceIn(-180.0, 180.0)
        val dayOfYear = date.dayOfYear

        val rad = Math.PI / 180.0
        val deg = 180.0 / Math.PI

        // Fractional year in radians
        val gamma = (2.0 * Math.PI / 365.0) * (dayOfYear - 1)

        // Equation of time (in minutes)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) -
                0.040849 * sin(2.0 * gamma)
            )

        // Solar declination angle (in radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2.0 * gamma) +
            0.000907 * sin(2.0 * gamma) -
            0.002697 * cos(3.0 * gamma) +
            0.00148 * sin(3.0 * gamma)

        val zenithRad = 90.833 * rad
        val latRad = safeLat * rad
        val cosHa = (
            (cos(zenithRad) / (cos(latRad) * cos(decl))) -
                (tan(latRad) * tan(decl))
            ).coerceIn(-1.0, 1.0)

        val haDeg = acos(cosHa) * deg
        val offsetSeconds = zoneId.rules.getOffset(date.atTime(12, 0)).totalSeconds
        val tzOffsetMinutes = offsetSeconds / 60.0

        val sunriseUtcMin = 720.0 - 4.0 * (safeLon + haDeg) - eqTime
        val sunsetUtcMin = 720.0 - 4.0 * (safeLon - haDeg) - eqTime

        val sunriseLocal = floor(sunriseUtcMin + tzOffsetMinutes).toInt().let { ((it % 1440) + 1440) % 1440 }
        val sunsetLocal = floor(sunsetUtcMin + tzOffsetMinutes).toInt().let { ((it % 1440) + 1440) % 1440 }

        return SolarTimes(
            sunriseMinuteOfDay = sunriseLocal,
            sunsetMinuteOfDay = sunsetLocal
        )
    }
}
