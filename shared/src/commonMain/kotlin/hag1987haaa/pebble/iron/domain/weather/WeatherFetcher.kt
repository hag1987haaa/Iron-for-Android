package hag1987haaa.pebble.iron.domain.weather

import hag1987haaa.pebble.iron.domain.model.WeatherCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val condition: WeatherCondition,
    val temperatureC: Double,
    val windSpeedMs: Double,
)

interface WeatherFetcher {
    suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot?
}

object OpenMeteoParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current: CurrentBlock? = null,
    )

    @Serializable
    private data class CurrentBlock(
        @SerialName("temperature_2m")
        val temperature2m: Double? = null,
        @SerialName("weather_code")
        val weatherCode: Int? = null,
        @SerialName("wind_speed_10m")
        val windSpeed10m: Double? = null,
    )

    fun buildUrl(latitude: Double, longitude: Double): String {
        val lat = ((latitude * 10000.0).roundToInt()) / 10000.0
        val lon = ((longitude * 10000.0).roundToInt()) / 10000.0
        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&wind_speed_unit=ms"
    }

    fun parseCurrentWeather(rawJson: String): WeatherSnapshot? {
        return try {
            val response = json.decodeFromString<OpenMeteoResponse>(rawJson)
            val current = response.current ?: return null
            val tempC = current.temperature2m ?: return null
            val code = current.weatherCode ?: return null
            val windMs = current.windSpeed10m ?: return null
            if (!tempC.isFinite() || !windMs.isFinite() || windMs < 0.0) return null
            WeatherSnapshot(
                condition = mapWmoCodeToCondition(code),
                temperatureC = (tempC * 10.0).roundToInt() / 10.0,
                windSpeedMs = (windMs * 10.0).roundToInt() / 10.0,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Maps WMO weather interpretation codes (Open-Meteo) to [WeatherCondition].
     */
    fun mapWmoCodeToCondition(code: Int): WeatherCondition = when (code) {
        0, 1 -> WeatherCondition.SUNNY
        2, 3, 45, 48 -> WeatherCondition.CLOUDY
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> WeatherCondition.RAINY
        else -> WeatherCondition.CLOUDY
    }
}
