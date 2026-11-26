package hag1987haaa.pebble.iron.domain.backup

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.BiologicalSex
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.model.WeatherCondition
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import hag1987haaa.pebble.iron.util.GpxCourse
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IronBackupBundleTest {

    @Test
    fun roundTripBundlePreservesWeatherNotesAndSettings() {
        val originalSettings = AppSettings().apply {
            isMetric = false
            userWeightKg = 64.5f
            userHeightCm = 172.0f
            userBodyFatPct = 15.2f
            userRestingHr = 52
            userBmrKcalPerDay = 1580f
            userSex = BiologicalSex.FEMALE
            userBirthDate = LocalDate(1990, 5, 14)
            setOrderedEnabledActivityTypes(listOf(ActivityType.TRAIL_RUNNING, ActivityType.TREADMILL, ActivityType.CYCLING))
            lastActivityType = ActivityType.TRAIL_RUNNING.name
            setHrZoneNotificationEnabled(ActivityType.TRAIL_RUNNING, true)
            setHrZoneRange(ActivityType.TRAIL_RUNNING, 130, 175)
            setMapZoomForActivity(ActivityType.TRAIL_RUNNING, 16)
            setMidTypes(ActivityType.TRAIL_RUNNING, listOf(4, 1, 0))
            setLowerTypes(ActivityType.TRAIL_RUNNING, listOf(5, 9))
            notificationDistanceStep = 2.0f
            notificationTimeSeconds = 300
            mapRouteColor = MapColorPreset.BLUE
            upLongPressMode = LongPressMode.INTENT
            wheelCircumferenceMm = 2136
            savedGpxCourses = listOf(
                GpxCourse(
                    id = "course-1",
                    name = "River Loop",
                    points = listOf(
                        LocationPoint(
                            latitude = 35.68,
                            longitude = 139.76,
                            altitude = 10.0,
                            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                        )
                    ),
                    totalDistanceMeters = 5200.0,
                    isEnabled = true,
                )
            )
            // Device-specific values that should NOT be exported/overwritten
            autoExportTcxUri = "content://device-specific/tcx"
            bleHeartRateDeviceAddress = "AA:BB:CC:DD:EE:FF"
        }

        val run = RunActivity(
            id = 42L,
            name = "Morning Trail",
            type = ActivityType.TRAIL_RUNNING,
            startTime = Instant.parse("2026-04-10T06:30:00Z"),
            endTime = Instant.parse("2026-04-10T07:15:00Z"),
            distanceMeters = 8120.0,
            durationSeconds = 2700L,
            calories = 520.0,
            weatherCondition = WeatherCondition.SUNNY.name,
            temperatureC = 18.5,
            windSpeedMs = 3.2,
            notes = "Felt great! Trail was dry.",
            route = listOf(
                LocationPoint(
                    latitude = 35.681,
                    longitude = 139.767,
                    altitude = 12.0,
                    speed = 3.1,
                    heartRate = 145,
                    steps = 100,
                    timestamp = Instant.parse("2026-04-10T06:30:01Z"),
                )
            )
        )

        val encoded = IronBackupCodec.encode(listOf(run), originalSettings, exportedAt = "2026-04-10T08:00:00Z")
        val decoded = IronBackupCodec.decode(encoded)

        assertEquals(IronBackupBundle.CURRENT_VERSION, decoded.backupVersion)
        assertEquals("2026-04-10T08:00:00Z", decoded.exportedAt)
        assertEquals(1, decoded.runs.size)
        val restoredRun = decoded.runs.first()
        assertEquals("Morning Trail", restoredRun.name)
        assertEquals(WeatherCondition.SUNNY.name, restoredRun.weatherCondition)
        assertEquals(18.5, restoredRun.temperatureC)
        assertEquals(3.2, restoredRun.windSpeedMs)
        assertEquals("Felt great! Trail was dry.", restoredRun.notes)
        assertEquals(1, restoredRun.route.size)

        val targetSettings = AppSettings().apply {
            autoExportTcxUri = "content://new-device/tcx"
            bleHeartRateDeviceAddress = "11:22:33:44:55:66"
        }
        var saveCalls = 0
        targetSettings.onSettingsChanged = { saveCalls++ }

        assertNotNull(decoded.settings)
        targetSettings.applySettingsBackup(decoded.settings!!)

        assertEquals(1, saveCalls)
        assertEquals(false, targetSettings.isMetric)
        assertEquals(64.5f, targetSettings.userWeightKg)
        assertEquals(172.0f, targetSettings.userHeightCm)
        assertEquals(15.2f, targetSettings.userBodyFatPct)
        assertEquals(52, targetSettings.userRestingHr)
        assertEquals(1580f, targetSettings.userBmrKcalPerDay)
        assertEquals(BiologicalSex.FEMALE, targetSettings.userSex)
        assertEquals(LocalDate(1990, 5, 14), targetSettings.userBirthDate)
        assertEquals(
            listOf(ActivityType.TRAIL_RUNNING, ActivityType.TREADMILL, ActivityType.CYCLING),
            targetSettings.getOrderedEnabledActivityTypes(),
        )
        assertTrue(targetSettings.isHrZoneNotificationEnabled(ActivityType.TRAIL_RUNNING))
        assertEquals(130, targetSettings.getHrZoneMin(ActivityType.TRAIL_RUNNING))
        assertEquals(175, targetSettings.getHrZoneMax(ActivityType.TRAIL_RUNNING))
        assertEquals(16, targetSettings.getMapZoomForActivity(ActivityType.TRAIL_RUNNING))
        assertEquals(listOf(4, 1, 0), targetSettings.getMidTypes(ActivityType.TRAIL_RUNNING))
        assertEquals(listOf(5, 9), targetSettings.getLowerTypes(ActivityType.TRAIL_RUNNING))
        assertEquals(2.0f, targetSettings.notificationDistanceStep)
        assertEquals(300, targetSettings.notificationTimeSeconds)
        assertEquals(MapColorPreset.BLUE, targetSettings.mapRouteColor)
        assertEquals(LongPressMode.INTENT, targetSettings.upLongPressMode)
        assertEquals(2136, targetSettings.wheelCircumferenceMm)
        assertEquals(1, targetSettings.savedGpxCourses.size)
        assertEquals("River Loop", targetSettings.savedGpxCourses.first().name)

        // Device-specific values on target device remain untouched
        assertEquals("content://new-device/tcx", targetSettings.autoExportTcxUri)
        assertEquals("11:22:33:44:55:66", targetSettings.bleHeartRateDeviceAddress)
    }

    @Test
    fun decodeSupportsLegacyArrayFormatAndUnknownKeys() {
        val legacyJson = """
            [
              {
                "id": 1,
                "name": "Legacy Run",
                "type": "RUNNING",
                "startTime": "2025-12-01T07:00:00Z",
                "distanceMeters": 5000.0,
                "durationSeconds": 1500,
                "futureUnknownField": "ignored"
              }
            ]
        """.trimIndent()

        val decoded = IronBackupCodec.decode(legacyJson)
        assertEquals(1, decoded.backupVersion)
        assertNull(decoded.settings)
        assertEquals(1, decoded.runs.size)
        assertEquals("Legacy Run", decoded.runs.first().name)
        assertNull(decoded.runs.first().weatherCondition)
        assertNull(decoded.runs.first().temperatureC)
        assertNull(decoded.runs.first().windSpeedMs)
        assertNull(decoded.runs.first().notes)
    }

    @Test
    fun decodeBundleIgnoresFutureUnknownFields() {
        val futureJson = """
            {
              "backupVersion": 99,
              "exportedAt": "2027-01-01T00:00:00Z",
              "futureTopLevel": true,
              "runs": [],
              "settings": {
                "isMetric": true,
                "futureSetting": 123
              }
            }
        """.trimIndent()

        val decoded = IronBackupCodec.decode(futureJson)
        assertEquals(99, decoded.backupVersion)
        assertEquals(0, decoded.runs.size)
        assertNotNull(decoded.settings)
        assertEquals(true, decoded.settings?.isMetric)
    }
}
