package hag1987haaa.pebble.iron.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    val timestamp: Instant,
    val isSegmentStart: Boolean = false,
    val speedAccuracyMetersPerSecond: Double? = null,
    val bearingAccuracyDegrees: Double? = null,
    val verticalAccuracyMeters: Double? = null,
    @Transient val elapsedRealtimeNanos: Long? = null,
)
