package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlin.math.abs

internal data class WorkoutElevationFilterConfig(
    val maximumVerticalAccuracyMeters: Double = 30.0,
    val minimumElevationNoiseFloorMeters: Double = 1.0,
)

internal class WorkoutElevationFilter(
    private val config: WorkoutElevationFilterConfig = WorkoutElevationFilterConfig(),
) {
    private var anchor: LocationPoint? = null

    fun process(
        candidate: LocationPoint,
        isSegmentStart: Boolean = false,
    ): Double {
        if (isSegmentStart) reset()
        if (!hasUsableElevation(candidate)) return 0.0

        val previous = anchor
        if (previous == null) {
            anchor = candidate
            return 0.0
        }

        val altitudeDelta = candidate.altitude!! - previous.altitude!!
        val noiseFloor = maxOf(
            previous.verticalAccuracyMeters!!,
            candidate.verticalAccuracyMeters!!,
            config.minimumElevationNoiseFloorMeters,
        )
        if (abs(altitudeDelta) < noiseFloor) return 0.0

        anchor = candidate
        return altitudeDelta.coerceAtLeast(0.0)
    }

    fun reset() {
        anchor = null
    }

    private fun hasUsableElevation(point: LocationPoint): Boolean {
        val altitude = point.altitude
        val accuracy = point.verticalAccuracyMeters
        return altitude != null && altitude.isFinite() &&
            accuracy != null && accuracy.isFinite() &&
            accuracy >= 0.0 && accuracy <= config.maximumVerticalAccuracyMeters
    }
}
