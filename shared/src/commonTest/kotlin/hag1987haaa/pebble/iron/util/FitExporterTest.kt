package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.model.WorkoutTypeRegistry
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitExporterTest {

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

    @Test
    fun exportsValidFitHeaderAndCrcForOutdoorClimbing() {
        val run = RunActivity(
            name = "Outdoor Climb",
            type = ActivityType.CLIMBING,
            startTime = Instant.parse("2026-04-10T08:00:00Z"),
            endTime = Instant.parse("2026-04-10T09:00:00Z"),
            distanceMeters = 450.0,
            durationSeconds = 3600L,
            calories = 420.0,
            avgHeartRate = 135.0,
            maxHeartRate = 165.0,
            elevationGain = 120.0,
            route = listOf(
                LocationPoint(
                    latitude = 35.6812,
                    longitude = 139.7671,
                    altitude = 100.0,
                    speed = 0.5,
                    heartRate = 130,
                    timestamp = Instant.parse("2026-04-10T08:00:05Z"),
                ),
                LocationPoint(
                    latitude = 35.6815,
                    longitude = 139.7674,
                    altitude = 115.0,
                    speed = 0.4,
                    heartRate = 142,
                    timestamp = Instant.parse("2026-04-10T08:00:10Z"),
                ),
            ),
        )

        val fit = FitExporter.export(run)
        assertTrue(fit.size > 16)

        // Header checks
        assertEquals(14, fit[0].toInt() and 0xFF)
        assertEquals(0x20, fit[1].toInt() and 0xFF)
        assertEquals('.'.code, fit[8].toInt() and 0xFF)
        assertEquals('F'.code, fit[9].toInt() and 0xFF)
        assertEquals('I'.code, fit[10].toInt() and 0xFF)
        assertEquals('T'.code, fit[11].toInt() and 0xFF)

        val dataSize = readUInt32LE(fit, 4).toInt()
        assertEquals(fit.size - 16, dataSize)

        val expectedHeaderCrc = FitExporter.computeCrc16(fit, 0, 12)
        assertEquals(expectedHeaderCrc, readUInt16LE(fit, 12))

        val expectedFileCrc = FitExporter.computeCrc16(fit, 14, 14 + dataSize)
        assertEquals(expectedFileCrc, readUInt16LE(fit, fit.size - 2))
    }

    @Test
    fun exportsValidFitFileForIndoorBoulderingWithoutGpsFields() {
        val run = RunActivity(
            name = "Gym Bouldering",
            type = ActivityType.BOULDERING,
            startTime = Instant.parse("2026-04-10T18:00:00Z"),
            endTime = Instant.parse("2026-04-10T19:00:00Z"),
            distanceMeters = 0.0,
            durationSeconds = 3600L,
            calories = 350.0,
            avgHeartRate = 128.0,
            maxHeartRate = 162.0,
            route = listOf(
                LocationPoint(latitude = 0.0, longitude = 0.0, heartRate = 125, timestamp = Instant.parse("2026-04-10T18:00:01Z")),
                LocationPoint(latitude = 0.0, longitude = 0.0, heartRate = 140, timestamp = Instant.parse("2026-04-10T18:00:02Z")),
            ),
        )

        val fit = FitExporter.export(run)
        val dataSize = readUInt32LE(fit, 4).toInt()
        assertEquals(fit.size - 16, dataSize)
        assertEquals(FitExporter.computeCrc16(fit, 14, 14 + dataSize), readUInt16LE(fit, fit.size - 2))

        // Verify FIT sport/sub_sport mapping for Climbing & Bouldering
        val climbingDef = WorkoutTypeRegistry.getDefinition(ActivityType.CLIMBING)
        val boulderingDef = WorkoutTypeRegistry.getDefinition(ActivityType.BOULDERING)
        assertEquals(31, climbingDef.fitSport)
        assertEquals(0, climbingDef.fitSubSport)
        assertEquals(true, climbingDef.usesGps)
        assertEquals(31, boulderingDef.fitSport)
        assertEquals(69, boulderingDef.fitSubSport)
        assertEquals(false, boulderingDef.usesGps)
    }
}
