package hag1987haaa.pebble.iron.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    val accuracy: Double? = null,
    val heartRate: Int? = null,
    val steps: Int? = null,
    val timestamp: Instant
)
