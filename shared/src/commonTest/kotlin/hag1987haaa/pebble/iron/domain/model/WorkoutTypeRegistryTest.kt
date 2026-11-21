package hag1987haaa.pebble.iron.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
