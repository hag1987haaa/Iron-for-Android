package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccuracyAwareLocationSmootherTest {
    private val smoother = AccuracyAwareLocationSmoother()

    @Test
    fun accurateFixHasMoreInfluenceThanInaccurateFix() {
        val accurate = point(latitude = 10.0, accuracy = 3.0)
        val inaccurate = point(latitude = 20.0, accuracy = 25.0)

        val smoothed = smoother.smooth(listOf(accurate, inaccurate))

        assertTrue(smoothed.latitude < 11.0)
        assertEquals(inaccurate.timestamp, smoothed.timestamp)
        assertEquals(inaccurate.accuracy, smoothed.accuracy)
    }

    @Test
    fun recencyStillInfluencesEquallyAccurateFixes() {
        val oldest = point(latitude = 0.0, accuracy = 5.0)
        val middle = point(latitude = 10.0, accuracy = 5.0)
        val latest = point(latitude = 20.0, accuracy = 5.0)

        val smoothed = smoother.smooth(listOf(oldest, middle, latest))

        assertEquals(15.714285714285714, smoothed.latitude, 0.000001)
    }

    @Test
    fun missingAccuracyKeepsExistingRecencyWeighting() {
        val oldest = point(latitude = 0.0, accuracy = 3.0)
        val latest = point(latitude = 10.0, accuracy = null)

        val smoothed = smoother.smooth(listOf(oldest, latest))

        assertEquals(8.0, smoothed.latitude, 0.000001)
    }

    @Test
    fun altitudeKeepsExistingRecencyOnlySmoothing() {
        val oldest = point(latitude = 0.0, accuracy = 3.0, altitude = 100.0)
        val latest = point(latitude = 10.0, accuracy = 25.0, altitude = 110.0)

        val smoothed = smoother.smooth(listOf(oldest, latest))

        assertEquals(108.0, smoothed.altitude)
    }

    @Test
    fun missingLatestAltitudeRemainsMissing() {
        val oldest = point(latitude = 0.0, accuracy = 5.0, altitude = 100.0)
        val latest = point(latitude = 10.0, accuracy = 5.0, altitude = null)

        val smoothed = smoother.smooth(listOf(oldest, latest))

        assertEquals(null, smoothed.altitude)
    }

    private fun point(
        latitude: Double,
        accuracy: Double?,
        altitude: Double? = 100.0,
    ): LocationPoint = LocationPoint(
        latitude = latitude,
        longitude = -77.0,
        altitude = altitude,
        accuracy = accuracy,
        timestamp = Instant.fromEpochMilliseconds((latitude * 1000.0).toLong()),
    )
}
