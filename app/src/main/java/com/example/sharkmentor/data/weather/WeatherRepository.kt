package com.example.sharkmentor.data.weather

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherSettings(
    val autoLocation: Boolean = true,
    val manualCity: String = "Sivas",
    val manualCountry: String = "Turkey",
    val manualLat: Double = 39.7477,
    val manualLon: Double = 37.0179,
    val useFahrenheit: Boolean = false
)

data class HourlyForecastItem(
    val hourLabel: String,
    val tempCelsius: Double,
    val weatherCode: Int,
    val isoTime: String = ""
)

data class WeatherUiState(
    val city: String = "Sivas",
    val country: String = "Turkey",
    val currentTempCelsius: Double? = null,
    val currentWeatherCode: Int = 0,
    val hourly: List<HourlyForecastItem> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null
)

data class GeocodingResult(
    val name: String,
    val country: String,
    val admin1: String = "",
    val latitude: Double,
    val longitude: Double
)

fun formatTemp(tempCelsius: Double, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val fahrenheit = (tempCelsius * 9.0 / 5.0) + 32.0
        "${fahrenheit.roundToInt()}°F"
    } else {
        "${tempCelsius.roundToInt()}°C"
    }
}

fun weatherConditionLabel(code: Int): String {
    return when (code) {
        0 -> "Açık"
        1 -> "Çoğunlukla açık"
        2 -> "Parçalı bulutlu"
        3 -> "Kapalı"
        45, 48 -> "Sisli"
        51, 53, 55 -> "Çiseleme"
        56, 57 -> "Dondurucu çiseleme"
        61, 63, 65 -> "Yağmurlu"
        66, 67 -> "Dondurucu yağmur"
        71, 73, 75 -> "Kar yağışlı"
        77 -> "Kar taneleri"
        80, 81, 82 -> "Sağanak yağışlı"
        85, 86 -> "Kar sağanağı"
        95 -> "Gök gürültülü fırtına"
        96, 99 -> "Dolu ve fırtına"
        else -> "Bilinmiyor"
    }
}

class WeatherRepository private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<WeatherSettings> = _settings.asStateFlow()

    private val _weatherState = MutableStateFlow(loadCachedWeather())
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private fun loadSettings(): WeatherSettings {
        return WeatherSettings(
            autoLocation = prefs.getBoolean(KEY_AUTO_LOCATION, true),
            manualCity = prefs.getString(KEY_MANUAL_CITY, "Sivas") ?: "Sivas",
            manualCountry = prefs.getString(KEY_MANUAL_COUNTRY, "Turkey") ?: "Turkey",
            manualLat = prefs.getString(KEY_MANUAL_LAT, null)?.toDoubleOrNull() ?: 39.7477,
            manualLon = prefs.getString(KEY_MANUAL_LON, null)?.toDoubleOrNull() ?: 37.0179,
            useFahrenheit = prefs.getBoolean(KEY_USE_FAHRENHEIT, false)
        )
    }

    private fun loadCachedWeather(): WeatherUiState {
        val currentSettings = loadSettings()
        val city = prefs.getString(KEY_CACHE_CITY, currentSettings.manualCity) ?: currentSettings.manualCity
        val country = prefs.getString(KEY_CACHE_COUNTRY, currentSettings.manualCountry) ?: currentSettings.manualCountry
        val hasTemp = prefs.contains(KEY_CACHE_TEMP)
        val temp = if (hasTemp) prefs.getString(KEY_CACHE_TEMP, null)?.toDoubleOrNull() else null
        val code = prefs.getInt(KEY_CACHE_CODE, 0)
        val lastUpdated = prefs.getLong(KEY_CACHE_UPDATED_AT, 0L)
        val hourlyJson = prefs.getString(KEY_CACHE_HOURLY, null)
        val hourlyList = parseHourlyCache(hourlyJson)

        return WeatherUiState(
            city = city,
            country = country,
            currentTempCelsius = temp,
            currentWeatherCode = code,
            hourly = hourlyList,
            isLoading = false,
            lastUpdatedMillis = lastUpdated,
            errorMessage = null
        )
    }

    private fun parseHourlyCache(json: String?): List<HourlyForecastItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                HourlyForecastItem(
                    hourLabel = obj.optString("hourLabel", ""),
                    tempCelsius = obj.optDouble("tempCelsius", 0.0),
                    weatherCode = obj.optInt("weatherCode", 0),
                    isoTime = obj.optString("isoTime", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveWeatherCache(state: WeatherUiState) {
        val hourlyArray = JSONArray()
        state.hourly.forEach { item ->
            val obj = JSONObject()
            obj.put("hourLabel", item.hourLabel)
            obj.put("tempCelsius", item.tempCelsius)
            obj.put("weatherCode", item.weatherCode)
            if (item.isoTime.isNotBlank()) obj.put("isoTime", item.isoTime)
            hourlyArray.put(obj)
        }
        prefs.edit().apply {
            putString(KEY_CACHE_CITY, state.city)
            putString(KEY_CACHE_COUNTRY, state.country)
            if (state.currentTempCelsius != null) {
                putString(KEY_CACHE_TEMP, state.currentTempCelsius.toString())
            } else {
                remove(KEY_CACHE_TEMP)
            }
            putInt(KEY_CACHE_CODE, state.currentWeatherCode)
            putLong(KEY_CACHE_UPDATED_AT, state.lastUpdatedMillis)
            putString(KEY_CACHE_HOURLY, hourlyArray.toString())
            apply()
        }
    }

    fun setAutoLocation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOCATION, enabled).apply()
        _settings.value = _settings.value.copy(autoLocation = enabled)
    }

    fun setUseFahrenheit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_FAHRENHEIT, enabled).apply()
        _settings.value = _settings.value.copy(useFahrenheit = enabled)
    }

    fun setManualLocation(city: String, country: String, lat: Double, lon: Double) {
        prefs.edit().apply {
            putBoolean(KEY_AUTO_LOCATION, false)
            putString(KEY_MANUAL_CITY, city)
            putString(KEY_MANUAL_COUNTRY, country)
            putString(KEY_MANUAL_LAT, lat.toString())
            putString(KEY_MANUAL_LON, lon.toString())
            apply()
        }
        _settings.value = _settings.value.copy(
            autoLocation = false,
            manualCity = city,
            manualCountry = country,
            manualLat = lat,
            manualLon = lon
        )
    }

    suspend fun refreshWeather(force: Boolean = false) {
        val currentState = _weatherState.value
        val now = System.currentTimeMillis()
        if (!force &&
            currentState.currentTempCelsius != null &&
            currentState.lastUpdatedMillis > 0L &&
            (now - currentState.lastUpdatedMillis) < CACHE_DURATION_MS
        ) {
            return
        }

        _weatherState.value = currentState.copy(isLoading = true, errorMessage = null)

        val resultState = withContext(Dispatchers.IO) {
            try {
                val activeSettings = _settings.value
                var targetCity = activeSettings.manualCity
                var targetCountry = activeSettings.manualCountry
                var targetLat = activeSettings.manualLat
                var targetLon = activeSettings.manualLon

                if (activeSettings.autoLocation) {
                    val geo = fetchDeviceLocation() ?: fetchGeoJsLocation()
                    if (geo != null) {
                        targetCity = geo.city
                        targetCountry = geo.country
                        targetLat = geo.lat
                        targetLon = geo.lon
                    } else if (currentState.city.isNotBlank() && currentState.country.isNotBlank()) {
                        targetCity = currentState.city
                        targetCountry = currentState.country
                    }
                }

                val forecastUrl =
                    "https://" + "api." + "open-meteo.com" + "/v1/forecast?latitude=$targetLat&longitude=$targetLon&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&timezone=auto&past_days=1&forecast_days=2"
                val responseText = httpGet(forecastUrl)
                val root = JSONObject(responseText)

                val currentObj = root.getJSONObject("current")
                val currentTemp = currentObj.getDouble("temperature_2m")
                val currentCode = currentObj.optInt("weather_code", 0)

                val hourlyObj = root.optJSONObject("hourly")
                val hourlyList = mutableListOf<HourlyForecastItem>()
                if (hourlyObj != null) {
                    val times = hourlyObj.optJSONArray("time")
                    val temps = hourlyObj.optJSONArray("temperature_2m")
                    val codes = hourlyObj.optJSONArray("weather_code")
                    if (times != null && temps != null && codes != null) {
                        val count = minOf(times.length(), temps.length(), codes.length())
                        for (i in 0 until count) {
                            val rawTime = times.optString(i, "")
                            val hourLabel = if (rawTime.contains("T")) {
                                rawTime.substringAfter("T").take(5)
                            } else {
                                rawTime.takeLast(5)
                            }
                            hourlyList.add(
                                HourlyForecastItem(
                                    hourLabel = hourLabel,
                                    tempCelsius = temps.optDouble(i, currentTemp),
                                    weatherCode = codes.optInt(i, currentCode),
                                    isoTime = rawTime
                                )
                            )
                        }
                    }
                }

                prefs.edit()
                    .putString(KEY_CACHE_LAT, targetLat.toString())
                    .putString(KEY_CACHE_LON, targetLon.toString())
                    .apply()

                val updated = WeatherUiState(
                    city = targetCity,
                    country = targetCountry,
                    currentTempCelsius = currentTemp,
                    currentWeatherCode = currentCode,
                    hourly = hourlyList,
                    isLoading = false,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
                saveWeatherCache(updated)
                updated
            } catch (e: Exception) {
                currentState.copy(
                    isLoading = false,
                    errorMessage = "İnternet bağlantısı sağlanamadığı için hava durumu verisi yenilenemedi."
                )
            }
        }

        _weatherState.value = resultState
    }

    suspend fun searchLocations(query: String): List<GeocodingResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url =
                "https://" + "geocoding-api." + "open-meteo.com" + "/v1/search?name=$encoded&count=6&language=tr&format=json"
            val responseText = httpGet(url)
            val root = JSONObject(responseText)
            val resultsArray = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<GeocodingResult>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val name = item.optString("name", "")
                val country = item.optString("country", "")
                val admin1 = item.optString("admin1", "")
                val lat = item.optDouble("latitude", Double.NaN)
                val lon = item.optDouble("longitude", Double.NaN)
                if (name.isNotBlank() && !lat.isNaN() && !lon.isNaN()) {
                    list.add(
                        GeocodingResult(
                            name = name,
                            country = country,
                            admin1 = admin1,
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class GeoJsLocation(
        val city: String,
        val country: String,
        val lat: Double,
        val lon: Double
    )
    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocation(): GeoJsLocation? {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val locationManager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            runCatching { locationManager.getProviders(true) }
                .getOrNull()
                ?.forEach { if (!contains(it)) add(it) }
        }

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                ?: continue
            if (bestLocation == null || loc.time > bestLocation.time ||
                (loc.time == bestLocation.time && loc.accuracy < bestLocation.accuracy)
            ) {
                bestLocation = loc
            }
        }
        val location = bestLocation ?: return null
        val lat = location.latitude
        val lon = location.longitude

        var city = ""
        var country = "Turkey"
        runCatching {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(appContext, Locale("tr", "TR")).getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                city = (addr.adminArea ?: addr.subAdminArea ?: addr.locality ?: "").trim()
                country = (addr.countryName ?: "Turkey").trim().ifBlank { "Turkey" }
            }
        }
        if (city.isBlank()) {
            city = "Konumum"
        }
        return GeoJsLocation(city = city, country = country, lat = lat, lon = lon)
    }
    
    private fun fetchGeoJsLocation(): GeoJsLocation? {
        return try {
            val responseText = httpGet("https://" + "get." + "geojs.io" + "/v1/ip/geo.json")
            val obj = JSONObject(responseText)
            val city = obj.optString("city", "").ifBlank { "Sivas" }
            val country = obj.optString("country", "").ifBlank { "Turkey" }
            val lat = obj.optString("latitude", "").toDoubleOrNull() ?: 39.7477
            val lon = obj.optString("longitude", "").toDoubleOrNull() ?: 37.0179
            GeoJsLocation(city = city, country = country, lat = lat, lon = lon)
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    fun getEffectiveCoordinates(): Pair<Double, Double> {
        val s = _settings.value
        if (!s.autoLocation) {
            return s.manualLat to s.manualLon
        }
        val cachedLat = prefs.getString(KEY_CACHE_LAT, null)?.toDoubleOrNull()
        val cachedLon = prefs.getString(KEY_CACHE_LON, null)?.toDoubleOrNull()
        if (cachedLat != null && cachedLon != null) {
            return cachedLat to cachedLon
        }
        return s.manualLat to s.manualLon
    }

    companion object {
        private const val PREFS_NAME = "shark_weather_prefs"
        private const val CACHE_DURATION_MS = 30L * 60L * 1000L

        private const val KEY_AUTO_LOCATION = "auto_location"
        private const val KEY_MANUAL_CITY = "manual_city"
        private const val KEY_MANUAL_COUNTRY = "manual_country"
        private const val KEY_MANUAL_LAT = "manual_lat"
        private const val KEY_MANUAL_LON = "manual_lon"
        private const val KEY_USE_FAHRENHEIT = "use_fahrenheit"

        private const val KEY_CACHE_CITY = "cache_city"
        private const val KEY_CACHE_COUNTRY = "cache_country"
        private const val KEY_CACHE_TEMP = "cache_temp"
        private const val KEY_CACHE_CODE = "cache_code"
        private const val KEY_CACHE_UPDATED_AT = "cache_updated_at"
        private const val KEY_CACHE_HOURLY = "cache_hourly"
        private const val KEY_CACHE_LAT = "cache_lat"
        private const val KEY_CACHE_LON = "cache_lon"

        @Volatile
        private var instance: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
