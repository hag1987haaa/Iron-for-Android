package hag1987haaa.pebble.iron.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxImporterTest {

    @Test
    fun testParseStandardTrkptGpx() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test">
              <metadata>
                <name>Tokyo Imperial Palace Loop</name>
              </metadata>
              <trk>
                <name>Tokyo Imperial Palace Loop</name>
                <trkseg>
                  <trkpt lat="35.6812" lon="139.7671">
                    <ele>10.5</ele>
                    <time>2026-09-12T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="35.6850" lon="139.7671">
                    <ele>12.0</ele>
                    <time>2026-09-12T10:01:00Z</time>
                  </trkpt>
                  <trkpt lat="35.6850" lon="139.7600">
                    <ele>15.2</ele>
                    <time>2026-09-12T10:02:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val course = GpxImporter.parse(gpx)
        assertNotNull(course)
        assertEquals("Tokyo Imperi", course.name)
        assertEquals(3, course.points.size)
        assertEquals(35.6812, course.points[0].latitude)
        assertEquals(139.7671, course.points[0].longitude)
        assertEquals(10.5, course.points[0].altitude)
        assertTrue(course.totalDistanceMeters > 500.0)
    }

    @Test
    fun testParseSelfClosingAndRtept() {
        val gpx = """
            <gpx version="1.1">
              <rte>
                <name>Cycling Route</name>
                <rtept lat="35.6800" lon="139.7600" />
                <rtept lat="35.6900" lon="139.7700" />
              </rte>
            </gpx>
        """.trimIndent()

        val course = GpxImporter.parse(gpx)
        assertNotNull(course)
        assertEquals("Cycling Rout", course.name)
        assertEquals(2, course.points.size)
        assertEquals(35.6800, course.points[0].latitude)
        assertEquals(139.7600, course.points[0].longitude)
        assertTrue(course.totalDistanceMeters > 1000.0)
    }

    @Test
    fun testParseEmptyOrInvalid() {
        assertNull(GpxImporter.parse(""))
        assertNull(GpxImporter.parse("   "))
        assertNull(GpxImporter.parse("<gpx><name>No Points</name></gpx>"))
    }

    @Test
    fun testSanitizeCourseName() {
        assertEquals("COURSE", GpxImporter.sanitizeCourseName(""))
        assertEquals("My_Route", GpxImporter.sanitizeCourseName("My,Route.gpx"))
        assertEquals("Route_1_sub", GpxImporter.sanitizeCourseName("Route:1|sub"))
        assertEquals("Tokyo Imperi", GpxImporter.sanitizeCourseName("Tokyo Imperial Palace"))
    }
}
