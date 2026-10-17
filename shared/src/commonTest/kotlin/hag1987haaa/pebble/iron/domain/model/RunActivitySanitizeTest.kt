package hag1987haaa.pebble.iron.domain.model

import hag1987haaa.pebble.iron.util.GpxExporter
import hag1987haaa.pebble.iron.util.TcxExporter
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunActivitySanitizeTest {

    @Test
    fun `expands workout start and end times when GPS timestamps slightly precede startTime or exceed endTime`() {
        val workoutStart = Instant.parse("2026-10-16T10:00:05Z")
        val workoutEnd = Instant.parse("2026-10-16T10:10:00Z")

        val p1 = LocationPoint(
            latitude = 35.0,
            longitude = 139.0,
            timestamp = Instant.parse("2026-10-16T10:00:02Z"), // 3s before workoutStart
            heartRate = 120
        )
        val p2 = LocationPoint(
            latitude = 35.001,
            longitude = 139.001,
            timestamp = Instant.parse("2026-10-16T10:00:03Z"), // 2s before workoutStart
            heartRate = 122
        )
        val p3 = LocationPoint(
            latitude = 35.002,
            longitude = 139.002,
            timestamp = Instant.parse("2026-10-16T10:10:02Z"), // 2s after workoutEnd
            heartRate = 150
        )

        val run = RunActivity(
            startTime = workoutStart,
            endTime = workoutEnd,
            durationSeconds = 595,
            distanceMeters = 1000.0,
            route = listOf(p1, p2, p3)
        )

        val sanitized = run.withSanitizedTimestamps()

        // startTime expands back to p1.timestamp, endTime expands forward to p3.timestamp
        assertEquals(Instant.parse("2026-10-16T10:00:02Z"), sanitized.startTime)
        assertEquals(Instant.parse("2026-10-16T10:10:02Z"), sanitized.endTime)

        // Spacing between p1 and p2 (1 second) is preserved without collapsing
        assertEquals(Instant.parse("2026-10-16T10:00:02Z"), sanitized.route[0].timestamp)
        assertEquals(Instant.parse("2026-10-16T10:00:03Z"), sanitized.route[1].timestamp)
        assertEquals(Instant.parse("2026-10-16T10:10:02Z"), sanitized.route[2].timestamp)

        // Every point is strictly within [startTime, endTime]
        sanitized.route.forEach { pt ->
            assertTrue(pt.timestamp >= sanitized.startTime)
            assertTrue(pt.timestamp <= sanitized.endTime!!)
        }
    }

    @Test
    fun `clamps stale cached point older than 15s to startTime and enforces monotonic order`() {
        val workoutStart = Instant.parse("2026-10-16T10:02:00Z")
        val workoutEnd = Instant.parse("2026-10-16T10:10:00Z")

        val stalePoint = LocationPoint(
            latitude = 35.0,
            longitude = 139.0,
            timestamp = Instant.parse("2026-10-16T10:00:30Z") // 90s before workoutStart (cached lastLocation)
        )
        val normalPoint = LocationPoint(
            latitude = 35.001,
            longitude = 139.001,
            timestamp = Instant.parse("2026-10-16T10:02:02Z")
        )
        val outOfOrderPoint = LocationPoint(
            latitude = 35.002,
            longitude = 139.002,
            timestamp = Instant.parse("2026-10-16T10:02:01Z") // 1s earlier than previous point
        )

        val run = RunActivity(
            startTime = workoutStart,
            endTime = workoutEnd,
            durationSeconds = 480,
            distanceMeters = 500.0,
            route = listOf(stalePoint, normalPoint, outOfOrderPoint)
        )

        val sanitized = run.withSanitizedTimestamps()

        // Stale point does not pull workoutStart back by 90s
        assertEquals(workoutStart, sanitized.startTime)
        assertEquals(workoutStart, sanitized.route[0].timestamp)
        assertEquals(Instant.parse("2026-10-16T10:02:02Z"), sanitized.route[1].timestamp)
        // Out-of-order point is clamped to previous point's timestamp
        assertEquals(Instant.parse("2026-10-16T10:02:02Z"), sanitized.route[2].timestamp)
    }

    @Test
    fun `tcx and gpx exporters output sanitized start time and trackpoint times`() {
        val workoutStart = Instant.parse("2026-10-16T10:00:05Z")
        val earlyPoint = LocationPoint(
            latitude = 35.0,
            longitude = 139.0,
            timestamp = Instant.parse("2026-10-16T10:00:03Z")
        )
        val run = RunActivity(
            startTime = workoutStart,
            endTime = Instant.parse("2026-10-16T10:05:00Z"),
            durationSeconds = 300,
            distanceMeters = 800.0,
            route = listOf(earlyPoint)
        )

        val tcx = TcxExporter.export(run)
        assertTrue(tcx.contains("<Id>2026-10-16T10:00:03Z</Id>"))
        assertTrue(tcx.contains("<Lap StartTime=\"2026-10-16T10:00:03Z\">"))
        assertTrue(tcx.contains("<Time>2026-10-16T10:00:03Z</Time>"))

        val gpx = GpxExporter.export(run)
        assertTrue(gpx.contains("<time>2026-10-16T10:00:03Z</time>"))
    }
}
