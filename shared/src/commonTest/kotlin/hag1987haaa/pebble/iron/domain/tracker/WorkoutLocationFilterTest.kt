package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutLocationFilterTest {
    private val filter = WorkoutLocationFilter()
    private val baseLatitude = 38.9586
    private val baseLongitude = -77.3570

    @Test
    fun acceptsNormalWalkingFix() {
        val previous = point(northMeters = 0.0, seconds = 0, speed = 1.4)
        val current = point(northMeters = 2.0, seconds = 1, speed = 1.4)

        assertTrue(
            filter.isAcceptable(previous, current, ActivityType.WALKING),
        )
    }

    @Test
    fun rejectsLargeWalkingTeleport() {
        val previous = point(northMeters = 0.0, seconds = 0, accuracy = 5.0)
        val current = point(northMeters = 60.0, seconds = 1, accuracy = 5.0)

        assertFalse(
            filter.isAcceptable(previous, current, ActivityType.WALKING),
        )
    }

    @Test
    fun rejectsVeryInaccurateFix() {
        val current = point(northMeters = 0.0, seconds = 0, accuracy = 80.0)

        assertFalse(
            filter.isAcceptable(null, current, ActivityType.HIKING),
        )
    }

    @Test
    fun allowsLegitimateFastCyclingFix() {
        val previous = point(
            northMeters = 0.0,
            seconds = 0,
            accuracy = 5.0,
            speed = 20.0,
        )
        val current = point(
            northMeters = 20.0,
            seconds = 1,
            accuracy = 5.0,
            speed = 20.0,
        )

        assertTrue(
            filter.isAcceptable(previous, current, ActivityType.CYCLING),
        )
    }

    @Test
    fun suppressesSmallDistanceNoise() {
        val previous =
            point(northMeters = 0.0, seconds = 0, accuracy = 5.0, speed = 1.4)
        val current =
            point(northMeters = 1.0, seconds = 1, accuracy = 5.0, speed = 1.4)

        assertFalse(
            filter.shouldAccumulateDistance(previous, current, 1.0),
        )
    }

    @Test
    fun accumulatesMovementAfterItClearsNoiseFloor() {
        val previous =
            point(northMeters = 0.0, seconds = 0, accuracy = 5.0, speed = 1.4)
        val current =
            point(northMeters = 3.0, seconds = 2, accuracy = 5.0, speed = 1.4)

        assertTrue(
            filter.shouldAccumulateDistance(previous, current, 3.0),
        )
    }

    @Test
    fun usesMonotonicFixTimeWhenWallClockMovesBackward() {
        val previous = point(
            northMeters = 0.0,
            seconds = 10,
            elapsedRealtimeNanos = 1_000_000_000L,
        )
        val current = point(
            northMeters = 10.0,
            seconds = 9,
            elapsedRealtimeNanos = 3_000_000_000L,
        )

        assertTrue(
            filter.isAcceptable(previous, current, ActivityType.WALKING),
        )
    }

    @Test
    fun monotonicFixTimePreventsWallClockFromHidingJump() {
        val previous = point(
            northMeters = 0.0,
            seconds = 0,
            elapsedRealtimeNanos = 1_000_000_000L,
        )
        val current = point(
            northMeters = 30.0,
            seconds = 60,
            elapsedRealtimeNanos = 2_000_000_000L,
        )

        assertFalse(
            filter.isAcceptable(previous, current, ActivityType.WALKING),
        )
    }

    @Test
    fun usesReportedSpeedAsStationaryHint() {
        val previous =
            point(northMeters = 0.0, seconds = 0, accuracy = 5.0, speed = 0.1)
        val current =
            point(northMeters = 4.0, seconds = 1, accuracy = 5.0, speed = 0.1)

        assertFalse(
            filter.shouldAccumulateDistance(previous, current, 4.0),
        )
    }

    private fun point(
        northMeters: Double,
        seconds: Long,
        accuracy: Double = 5.0,
        speed: Double? = 1.4,
        elapsedRealtimeNanos: Long? = null,
    ): LocationPoint = LocationPoint(
        latitude = baseLatitude + northMeters / 111_320.0,
        longitude = baseLongitude,
        altitude = 100.0,
        speed = speed,
        bearing = 0.0,
        accuracy = accuracy,
        timestamp = Instant.fromEpochMilliseconds(seconds * 1000L),
        elapsedRealtimeNanos = elapsedRealtimeNanos,
    )
}
