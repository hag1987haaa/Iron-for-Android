package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutElevationFilterTest {
    @Test
    fun ignoresStationaryAltitudeJitter() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = 4.0)))
        assertEquals(0.0, filter.process(point(altitude = 103.0, verticalAccuracy = 4.0)))
        assertEquals(0.0, filter.process(point(altitude = 98.0, verticalAccuracy = 4.0)))
    }

    @Test
    fun accumulatesGradualClimbFromDedicatedAnchor() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = 3.0)))
        assertEquals(0.0, filter.process(point(altitude = 102.0, verticalAccuracy = 3.0)))
        assertEquals(4.0, filter.process(point(altitude = 104.0, verticalAccuracy = 3.0)))
    }

    @Test
    fun poorVerticalAccuracyCannotMoveAnchorOrAddGain() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = 3.0)))
        assertEquals(0.0, filter.process(point(altitude = 140.0, verticalAccuracy = 50.0)))
        assertEquals(5.0, filter.process(point(altitude = 105.0, verticalAccuracy = 3.0)))
    }

    @Test
    fun missingOrInvalidElevationIsIgnored() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = null, verticalAccuracy = 3.0)))
        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = null)))
        assertEquals(0.0, filter.process(point(altitude = Double.NaN, verticalAccuracy = 3.0)))
        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = -1.0)))
    }

    @Test
    fun descentMovesAnchorWithoutAddingGain() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = 110.0, verticalAccuracy = 2.0)))
        assertEquals(0.0, filter.process(point(altitude = 105.0, verticalAccuracy = 2.0)))
        assertEquals(3.0, filter.process(point(altitude = 108.0, verticalAccuracy = 2.0)))
    }

    @Test
    fun segmentStartPreventsPauseGapFromAddingGain() {
        val filter = WorkoutElevationFilter()

        assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = 2.0)))
        assertEquals(
            0.0,
            filter.process(
                point(altitude = 120.0, verticalAccuracy = 2.0),
                isSegmentStart = true,
            ),
        )
        assertEquals(3.0, filter.process(point(altitude = 123.0, verticalAccuracy = 2.0)))
    }

    @Test
    fun policyIsSharedAcrossEveryWorkoutType() {
        ActivityType.entries.forEach { activityType ->
            val filter = WorkoutElevationFilter()

            assertEquals(0.0, filter.process(point(altitude = 100.0, verticalAccuracy = 2.0)))
            assertEquals(
                4.0,
                filter.process(point(altitude = 104.0, verticalAccuracy = 2.0)),
                activityType.name,
            )
        }
    }

    private fun point(
        altitude: Double?,
        verticalAccuracy: Double?,
    ): LocationPoint = LocationPoint(
        latitude = 38.9586,
        longitude = -77.3570,
        altitude = altitude,
        verticalAccuracyMeters = verticalAccuracy,
        timestamp = Instant.fromEpochMilliseconds(0L),
    )
}
