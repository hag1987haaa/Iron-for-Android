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
        assertEquals(7, types.size)
        assertEquals(0, types[0].id)
        assertEquals("RUNNING", types[0].name)
        assertEquals(ActivityType.RUNNING, types[0].baseActivityType)

        assertEquals(6, types[6].id)
        assertEquals("OTHER", types[6].name)
        assertEquals(ActivityType.OTHER, types[6].baseActivityType)
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
        val protocolStr = WorkoutTypeRegistry.toPebbleProtocolString(version = 1)
        val expected = "v:1|0,RUNNING,1,WALKING,2,CYCLING,3,HIKING,4,KAYAKING,5,ROWING,6,OTHER"
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
        ActivityType.entries.forEach { activityType ->
            val expectedZoom = if (activityType == ActivityType.CYCLING) 15 else 16
            assertEquals(expectedZoom, WorkoutTypeRegistry.getDefinition(activityType).watchDefaults.mapZoom)
            assertEquals(expectedZoom, settings.getMapZoomForActivity(activityType))
        }
    }

    @Test
    fun testFindByAssistantKeyword() {
        assertEquals(ActivityType.RUNNING, WorkoutTypeRegistry.findByAssistantKeyword("ランニング")?.baseActivityType)
        assertEquals(ActivityType.WALKING, WorkoutTypeRegistry.findByAssistantKeyword("散歩")?.baseActivityType)
        assertEquals(ActivityType.CYCLING, WorkoutTypeRegistry.findByAssistantKeyword("bike")?.baseActivityType)
        assertEquals(ActivityType.HIKING, WorkoutTypeRegistry.findByAssistantKeyword("登山")?.baseActivityType)
        assertEquals(ActivityType.KAYAKING, WorkoutTypeRegistry.findByAssistantKeyword("カヤック")?.baseActivityType)
        assertEquals(ActivityType.ROWING, WorkoutTypeRegistry.findByAssistantKeyword("ローイング")?.baseActivityType)
        assertNull(WorkoutTypeRegistry.findByAssistantKeyword("unknown_activity"))
    }
}
