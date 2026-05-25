package hag1987haaa.pebble.iron.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RunActivity(
    val id: Long = 0,
    val name: String? = null,
    val type: ActivityType = ActivityType.RUNNING,
    val startTime: Instant,
    val endTime: Instant? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val averagePaceSecondsPerKm: Double = 0.0,
    val calories: Double? = null,
    val steps: Int? = null,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val elevationGain: Double? = null,
    val healthConnectId: String? = null,
    val route: List<LocationPoint> = emptyList()
)
