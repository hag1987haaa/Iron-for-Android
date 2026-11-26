package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxExporterTest {

    @Test
    fun omitsZeroZeroIndoorPointsFromGpxTrack() {
        val indoorRun = RunActivity(
            name = "Treadmill Session",
            type = ActivityType.TREADMILL,
            startTime = Instant.parse("2026-04-10T08:00:00Z"),
            endTime = Instant.parse("2026-04-10T08:30:00Z"),
            durationSeconds = 1800,
            route = listOf(
                LocationPoint(latitude = 0.0, longitude = 0.0, heartRate = 140, timestamp = Instant.parse("2026-04-10T08:00:01Z")),
                LocationPoint(latitude = 0.0, longitude = 0.0, heartRate = 145, timestamp = Instant.parse("2026-04-10T08:00:02Z")),
            )
        )

        val gpx = GpxExporter.export(indoorRun)
        assertFalse(gpx.contains("<trkpt"), "GPX must not emit (0.0, 0.0) trackpoints for indoor workouts")
    }

    @Test
    fun exportsValidOutdoorPointsToGpxTrack() {
        val outdoorRun = RunActivity(
            name = "Morning Run",
            type = ActivityType.RUNNING,
            startTime = Instant.parse("2026-04-10T08:00:00Z"),
            endTime = Instant.parse("2026-04-10T08:10:00Z"),
            durationSeconds = 600,
            route = listOf(
                LocationPoint(latitude = 35.6812, longitude = 139.7671, heartRate = 150, timestamp = Instant.parse("2026-04-10T08:00:01Z")),
            )
        )

        val gpx = GpxExporter.export(outdoorRun)
        assertTrue(gpx.contains("<trkpt lat=\"35.6812\" lon=\"139.7671\">"))
        assertTrue(gpx.contains("<gpxtpx:hr>150</gpxtpx:hr>"))
    }
}
