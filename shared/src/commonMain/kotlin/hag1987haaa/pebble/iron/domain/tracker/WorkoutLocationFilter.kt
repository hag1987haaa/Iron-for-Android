package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.util.LocationUtils

/**
 * Small, conservative GPS quality layer for workout tracking.
 *
 * The existing three-point route smoothing remains in RunTrackerEngine.
 * This class only:
 *  - rejects invalid or very inaccurate fixes,
 *  - rejects implausible jumps for the selected activity,
 *  - suppresses tiny distance changes that are likely horizontal GPS noise.
 *
 * Keeping the policy isolated leaves a straightforward upgrade path for
 * richer Android location metadata or a more advanced estimator later.
 */
internal data class WorkoutLocationFilterConfig(
    val maximumAccuracyMeters: Double = 50.0,
    val jumpAccuracyAllowanceFraction: Double = 0.5,
    val jumpAccuracyAllowanceMeters: Double = 20.0,
    val distanceAccuracyFraction: Double = 0.35,
    val minimumDistanceNoiseFloorMeters: Double = 2.0,
    val maximumDistanceNoiseFloorMeters: Double = 8.0,
    val stationarySpeedMetersPerSecond: Double = 0.35,
    val maximumStationaryAccuracyRadiusMeters: Double = 10.0,
)

internal class WorkoutLocationFilter(
    private val config: WorkoutLocationFilterConfig = WorkoutLocationFilterConfig(),
) {
    fun isAcceptable(
        previousAccepted: LocationPoint?,
        candidate: LocationPoint,
        activityType: ActivityType,
    ): Boolean {
        if (!candidate.latitude.isFinite() ||
            !candidate.longitude.isFinite() ||
            candidate.latitude !in -90.0..90.0 ||
            candidate.longitude !in -180.0..180.0
        ) {
            return false
        }

        val candidateAccuracy = usableAccuracy(candidate)
        if (candidate.accuracy != null && candidateAccuracy == null) {
            return false
        }
        if (candidateAccuracy != null &&
            candidateAccuracy > config.maximumAccuracyMeters
        ) {
            return false
        }

        val previous = previousAccepted ?: return true
        val elapsedSeconds = elapsedSeconds(previous, candidate) ?: return false

        val distanceMeters = LocationUtils.calculateDistance(
            previous.latitude,
            previous.longitude,
            candidate.latitude,
            candidate.longitude,
        )
        if (!distanceMeters.isFinite()) {
            return false
        }

        val previousAccuracy = usableAccuracy(previous)
        val averageAccuracy = when {
            previousAccuracy != null && candidateAccuracy != null ->
                (previousAccuracy + candidateAccuracy) / 2.0
            previousAccuracy != null -> previousAccuracy
            candidateAccuracy != null -> candidateAccuracy
            else -> 0.0
        }

        // Give uncertain fixes a bounded allowance. This prevents the gate from
        // being brittle while still rejecting large one-second teleports.
        val uncertaintyAllowance =
            (averageAccuracy * config.jumpAccuracyAllowanceFraction)
                .coerceAtMost(config.jumpAccuracyAllowanceMeters)

        val maximumTravelMeters =
            maximumReasonableSpeedMetersPerSecond(activityType) * elapsedSeconds +
                uncertaintyAllowance

        return distanceMeters <= maximumTravelMeters
    }

    /**
     * Distance is measured from a separate anchor. When a movement is below the
     * uncertainty-aware floor the anchor stays where it is, allowing legitimate
     * slow movement to accumulate instead of losing every small sample.
     */
    fun shouldAccumulateDistance(
        previousDistancePoint: LocationPoint,
        candidate: LocationPoint,
        distanceMeters: Double,
    ): Boolean {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) {
            return false
        }

        val noiseFloor =
            distanceNoiseFloorMeters(previousDistancePoint, candidate)

        // FusedLocationProvider speed is only a conservative stationary hint.
        // It is never required for distance to accumulate.
        val reportedSpeed =
            candidate.speed?.takeIf { it.isFinite() && it >= 0.0 }

        val speedAccuracy =
            candidate.speedAccuracyMetersPerSecond
                ?.takeIf { it.isFinite() && it >= 0.0 }
        val stationarySpeedWithUncertainty =
            when {
                reportedSpeed == null -> null
                speedAccuracy != null -> reportedSpeed + speedAccuracy
                else -> reportedSpeed
            }

        if (stationarySpeedWithUncertainty != null &&
            stationarySpeedWithUncertainty < config.stationarySpeedMetersPerSecond
        ) {
            val accuracyRadius = maxOf(
                usableAccuracy(previousDistancePoint) ?: 0.0,
                usableAccuracy(candidate) ?: 0.0,
            ).coerceAtMost(config.maximumStationaryAccuracyRadiusMeters)

            if (distanceMeters <= maxOf(noiseFloor, accuracyRadius)) {
                return false
            }
        }

        return distanceMeters >= noiseFloor
    }

    private fun distanceNoiseFloorMeters(
        previous: LocationPoint,
        current: LocationPoint,
    ): Double {
        val accuracy = maxOf(
            usableAccuracy(previous) ?: 0.0,
            usableAccuracy(current) ?: 0.0,
        )

        return (accuracy * config.distanceAccuracyFraction).coerceIn(
            config.minimumDistanceNoiseFloorMeters,
            config.maximumDistanceNoiseFloorMeters,
        )
    }

    private fun elapsedSeconds(
        previous: LocationPoint,
        current: LocationPoint,
    ): Double? {
        val previousElapsedNanos = previous.elapsedRealtimeNanos
        val currentElapsedNanos = current.elapsedRealtimeNanos

        if (previousElapsedNanos != null && currentElapsedNanos != null) {
            val deltaNanos = currentElapsedNanos - previousElapsedNanos
            if (deltaNanos <= 0L) return null
            return deltaNanos / 1_000_000_000.0
        }

        val deltaMillis =
            current.timestamp.toEpochMilliseconds() -
                previous.timestamp.toEpochMilliseconds()
        if (deltaMillis <= 0L) return null
        return deltaMillis / 1000.0
    }

    private fun usableAccuracy(point: LocationPoint): Double? {
        val accuracy = point.accuracy ?: return null
        return accuracy.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun maximumReasonableSpeedMetersPerSecond(
        activityType: ActivityType,
    ): Double = when (activityType) {
        ActivityType.WALKING,
        ActivityType.HIKING -> 7.0

        ActivityType.RUNNING -> 12.0
        ActivityType.CYCLING -> 30.0

        ActivityType.KAYAKING,
        ActivityType.ROWING -> 15.0

        ActivityType.OTHER -> 35.0
    }
}
