package hag1987haaa.pebble.iron.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY,
    SNOWY;

    companion object {
        fun fromNameOrNull(value: String?): WeatherCondition? =
            value?.let { v -> entries.find { it.name.equals(v, ignoreCase = true) } }
    }
}
