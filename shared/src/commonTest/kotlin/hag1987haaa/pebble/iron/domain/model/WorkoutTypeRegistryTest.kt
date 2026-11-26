package hag1987haaa.pebble.iron.domain.model

import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutTypeRegistryTest {

    @Test
    fun testDefaultWorkoutTypes() {
        val types = WorkoutTypeRegistry.getAllTypes()
        assertEquals(18, types.size)
        assertEquals(0, types[0].id)
        assertEquals("RUNNING", types[0].name)
        assertEquals(ActivityType.RUNNING, types[0].baseActivityType)

        assertEquals(6, types[6].id)
        assertEquals("OTHER", types[6].name)
        assertEquals(ActivityType.OTHER, types[6].baseActivityType)

        assertEquals(15, types[15].id)
        assertEquals("STRENGTH_TRAINING", types[15].name)
        assertEquals("STRENGTH", types[15].pebbleLabel)
        assertEquals(ActivityType.STRENGTH_TRAINING, types[15].baseActivityType)

        assertEquals(16, types[16].id)
        assertEquals("CLIMBING", types[16].name)
        assertEquals(ActivityType.CLIMBING, types[16].baseActivityType)

        assertEquals(17, types[17].id)
        assertEquals("BOULDERING", types[17].name)
        assertEquals(ActivityType.BOULDERING, types[17].baseActivityType)
    }

    @Test
    fun testFindById() {
        val running = WorkoutTypeRegistry.findById(0)
        assertNotNull(running)
        assertEquals("RUNNING", running.name)

        val cycling = WorkoutTypeRegistry.findById(2)
        assertNotNull(cycling)
        assertEquals("CYCLING", cycling.name)
        assertEquals(ActivityType.CYCLING, cycling.baseActivityType)

        val nonExistent = WorkoutTypeRegistry.findById(999)
        assertNull(nonExistent)
    }

    @Test
    fun testFindByName() {
        val walking = WorkoutTypeRegistry.findByName("walking")
        assertNotNull(walking)
        assertEquals(1, walking.id)
        assertEquals(ActivityType.WALKING, walking.baseActivityType)

        val hiking = WorkoutTypeRegistry.findByName("HIKING")
        assertNotNull(hiking)
        assertEquals(3, hiking.id)

        assertNull(WorkoutTypeRegistry.findByName("NON_EXISTENT"))
    }

    @Test
    fun testFindByActivityType() {
        val rowing = WorkoutTypeRegistry.findByActivityType(ActivityType.ROWING)
        assertNotNull(rowing)
        assertEquals(5, rowing.id)
        assertEquals("ROWING", rowing.name)
    }

    @Test
    fun testToPebbleProtocolString() {
        val protocolStr = WorkoutTypeRegistry.toPebbleProtocolString(version = 2)
        val expected = "v:2|0,RUNNING,1,WALKING,2,CYCLING,3,HIKING,4,KAYAKING,5,ROWING,6,OTHER," +
            "7,TRAIL RUN,8,MTB,9,SKIING,10,SNOWBOARD,11,SUP,12,INLINE SKATE,13,TREADMILL,14,INDOOR BIKE,15,STRENGTH," +
            "16,CLIMBING,17,BOULDERING"
        assertEquals(expected, protocolStr)
    }

    @Test
    fun testAllActivityTypesAreRegisteredWithValidDefinitions() {
        val types = WorkoutTypeRegistry.getAllTypes()
        assertEquals(ActivityType.entries.size, types.size)

        val ids = types.map { it.id }.toSet()
        assertEquals(types.size, ids.size, "WorkoutTypeDefinition IDs must be unique")

        val names = types.map { it.name }.toSet()
        assertEquals(types.size, names.size, "WorkoutTypeDefinition names must be unique")

        val settings = AppSettings()

        ActivityType.entries.forEach { activityType ->
            val def = WorkoutTypeRegistry.getDefinition(activityType)
            assertEquals(activityType, def.baseActivityType)
            assertEquals(def, WorkoutTypeRegistry.findByActivityType(activityType))
            assertEquals(def, WorkoutTypeRegistry.findById(def.id))
            assertEquals(def, WorkoutTypeRegistry.findByName(def.name))

            assertTrue(def.maxReasonableSpeedMps > 0.0, "maxReasonableSpeedMps must be positive for $activityType")
            assertTrue(def.pebbleLabel.isNotBlank() && def.pebbleLabel.length <= 15, "pebbleLabel must be 1..15 chars for $activityType")
            assertTrue(def.cadenceUnit == "spm" || def.cadenceUnit == "rpm", "Unexpected cadenceUnit for $activityType")
            assertTrue(def.stepCadenceWindowSec >= 0, "stepCadenceWindowSec must be non-negative for $activityType")
            assertTrue(def.tcxSportType.isNotBlank(), "tcxSportType must not be blank for $activityType")

            val watch = def.watchDefaults
            assertTrue(watch.mapZoom in 11..18, "mapZoom out of range for $activityType")
            assertTrue(watch.hrZoneMin in 50..190, "hrZoneMin out of range for $activityType")
            assertTrue(watch.hrZoneMax in 80..220, "hrZoneMax out of range for $activityType")
            assertTrue(watch.hrZoneMin < watch.hrZoneMax, "hrZoneMin must be less than hrZoneMax for $activityType")
            assertTrue(watch.midTypes.isNotEmpty(), "midTypes must not be empty for $activityType")
            assertTrue(watch.lowerTypes.isNotEmpty(), "lowerTypes must not be empty for $activityType")

            // AppSettings のデフォルト値がマスター定義と一致すること
            assertEquals(watch.mapZoom, settings.getMapZoomForActivity(activityType))
            assertEquals(watch.hrZoneMin, settings.getHrZoneMin(activityType))
            assertEquals(watch.hrZoneMax, settings.getHrZoneMax(activityType))
            assertEquals(watch.midTypes, settings.getMidTypes(activityType))
            assertEquals(watch.lowerTypes, settings.getLowerTypes(activityType))
        }
    }

    @Test
    fun testDefaultMapZoomPolicy() {
        val settings = AppSettings()
        val wideZoomTypes = setOf(
            ActivityType.CYCLING,
            ActivityType.SKIING,
            ActivityType.SNOWBOARDING,
            ActivityType.INDOOR_CYCLING
        )
        ActivityType.entries.forEach { activityType ->
            val expectedZoom = if (activityType in wideZoomTypes) 15 else 16
            assertEquals(expectedZoom, WorkoutTypeRegistry.getDefinition(activityType).watchDefaults.mapZoom)
            assertEquals(expectedZoom, settings.getMapZoomForActivity(activityType))
        }
    }

    @Test
    fun testUsesGpsPolicy() {
        val indoorTypes = setOf(
            ActivityType.TREADMILL,
            ActivityType.INDOOR_CYCLING,
            ActivityType.STRENGTH_TRAINING,
            ActivityType.BOULDERING,
        )
        ActivityType.entries.forEach { activityType ->
            val expectedUsesGps = activityType !in indoorTypes
            assertEquals(expectedUsesGps, WorkoutTypeRegistry.getDefinition(activityType).usesGps, "Unexpected usesGps for $activityType")
        }
    }

    @Test
    fun testFindByAssistantKeyword() {
        assertEquals(ActivityType.RUNNING, WorkoutTypeRegistry.findByAssistantKeyword("ランニング")?.baseActivityType)
        assertEquals(ActivityType.TRAIL_RUNNING, WorkoutTypeRegistry.findByAssistantKeyword("トレイルラン")?.baseActivityType)
        assertEquals(ActivityType.WALKING, WorkoutTypeRegistry.findByAssistantKeyword("散歩")?.baseActivityType)
        assertEquals(ActivityType.CYCLING, WorkoutTypeRegistry.findByAssistantKeyword("bike")?.baseActivityType)
        assertEquals(ActivityType.MTB, WorkoutTypeRegistry.findByAssistantKeyword("マウンテンバイク")?.baseActivityType)
        assertEquals(ActivityType.HIKING, WorkoutTypeRegistry.findByAssistantKeyword("登山")?.baseActivityType)
        assertEquals(ActivityType.KAYAKING, WorkoutTypeRegistry.findByAssistantKeyword("カヤック")?.baseActivityType)
        assertEquals(ActivityType.ROWING, WorkoutTypeRegistry.findByAssistantKeyword("ローイング")?.baseActivityType)
        assertEquals(ActivityType.INLINE_SKATING, WorkoutTypeRegistry.findByAssistantKeyword("インラインスケート")?.baseActivityType)
        assertEquals(ActivityType.TREADMILL, WorkoutTypeRegistry.findByAssistantKeyword("トレッドミル")?.baseActivityType)
        assertEquals(ActivityType.INDOOR_CYCLING, WorkoutTypeRegistry.findByAssistantKeyword("インドアバイク")?.baseActivityType)
        assertEquals(ActivityType.STRENGTH_TRAINING, WorkoutTypeRegistry.findByAssistantKeyword("筋トレ")?.baseActivityType)
        assertEquals(ActivityType.CLIMBING, WorkoutTypeRegistry.findByAssistantKeyword("クライミング")?.baseActivityType)
        assertEquals(ActivityType.BOULDERING, WorkoutTypeRegistry.findByAssistantKeyword("ボルダリング")?.baseActivityType)
        assertNull(WorkoutTypeRegistry.findByAssistantKeyword("unknown_activity"))
    }

    @Test
    fun testEnabledActivityTypesOrderAndGuard() {
        val settings = AppSettings()
        assertEquals(ActivityType.entries, settings.getOrderedEnabledActivityTypes())

        val customOrder = listOf(ActivityType.TRAIL_RUNNING, ActivityType.CYCLING, ActivityType.TREADMILL)
        settings.setOrderedEnabledActivityTypes(customOrder)
        assertEquals(customOrder, settings.getOrderedEnabledActivityTypes())

        // 空リストは無視され、最低1件が維持される
        settings.setOrderedEnabledActivityTypes(emptyList())
        assertEquals(customOrder, settings.getOrderedEnabledActivityTypes())

        // カスタム順の Pebble プロトコル文字列
        val defs = settings.getOrderedEnabledActivityTypes().map { WorkoutTypeRegistry.getDefinition(it) }
        assertEquals("v:2|7,TRAIL RUN,2,CYCLING,13,TREADMILL", WorkoutTypeRegistry.toPebbleProtocolString(defs, version = 2))

        // リセットで全16種別に戻る
        settings.resetEnabledActivityTypes()
        assertEquals(ActivityType.entries, settings.getOrderedEnabledActivityTypes())
    }

    @Test
    fun testIndoorLocationPointGpsValidationAndTcxExport() {
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val indoorPt = LocationPoint(latitude = 0.0, longitude = 0.0, heartRate = 142, steps = 50, timestamp = now)
        val gpsPt = LocationPoint(latitude = 35.6812, longitude = 139.7671, heartRate = 142, timestamp = now)

        assertEquals(false, indoorPt.hasValidGpsCoordinate())
        assertEquals(true, gpsPt.hasValidGpsCoordinate())
        assertEquals(false, listOf(indoorPt).hasValidGpsRoute())
        assertEquals(true, listOf(indoorPt, gpsPt).hasValidGpsRoute())

        val indoorRun = RunActivity(
            type = ActivityType.TREADMILL,
            startTime = now,
            endTime = kotlinx.datetime.Instant.fromEpochMilliseconds(1_700_000_060_000L),
            durationSeconds = 60,
            distanceMeters = 0.0,
            calories = 10.0,
            route = listOf(indoorPt)
        )
        val tcx = hag1987haaa.pebble.iron.util.TcxExporter.export(indoorRun)
        assertTrue(!tcx.contains("<Position>"), "Indoor TCX should omit <Position> for 0.0,0.0 points")
        assertTrue(tcx.contains("<HeartRateBpm><Value>142</Value></HeartRateBpm>"), "Indoor TCX should keep heart rate")
    }
}
