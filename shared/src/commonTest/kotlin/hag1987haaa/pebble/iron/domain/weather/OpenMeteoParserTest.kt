package hag1987haaa.pebble.iron.domain.weather

import hag1987haaa.pebble.iron.domain.model.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMeteoParserTest {

    @Test
    fun parsesValidOpenMeteoCurrentResponse() {
        val sampleJson = """
            {
              "latitude": 35.6895,
              "longitude": 139.6917,
              "current": {
                "time": "2026-04-10T07:00",
                "interval": 900,
                "temperature_2m": 19.34,
                "weather_code": 0,
                "wind_speed_10m": 2.46
              }
            }
        """.trimIndent()

        val snapshot = OpenMeteoParser.parseCurrentWeather(sampleJson)
        assertNotNull(snapshot)
        assertEquals(WeatherCondition.SUNNY, snapshot.condition)
        assertEquals(19.3, snapshot.temperatureC)
        assertEquals(2.5, snapshot.windSpeedMs)
    }

    @Test
    fun mapsWmoWeatherCodesAccurately() {
        assertEquals(WeatherCondition.SUNNY, OpenMeteoParser.mapWmoCodeToCondition(0))
        assertEquals(WeatherCondition.SUNNY, OpenMeteoParser.mapWmoCodeToCondition(1))
        assertEquals(WeatherCondition.CLOUDY, OpenMeteoParser.mapWmoCodeToCondition(2))
        assertEquals(WeatherCondition.CLOUDY, OpenMeteoParser.mapWmoCodeToCondition(3))
        assertEquals(WeatherCondition.CLOUDY, OpenMeteoParser.mapWmoCodeToCondition(45))
        assertEquals(WeatherCondition.RAINY, OpenMeteoParser.mapWmoCodeToCondition(61))
        assertEquals(WeatherCondition.RAINY, OpenMeteoParser.mapWmoCodeToCondition(80))
        assertEquals(WeatherCondition.RAINY, OpenMeteoParser.mapWmoCodeToCondition(95))
        assertEquals(WeatherCondition.SNOWY, OpenMeteoParser.mapWmoCodeToCondition(73))
        assertEquals(WeatherCondition.SNOWY, OpenMeteoParser.mapWmoCodeToCondition(85))
    }

    @Test
    fun returnsNullOnMalformedOrIncompleteJson() {
        assertNull(OpenMeteoParser.parseCurrentWeather("not json"))
        assertNull(OpenMeteoParser.parseCurrentWeather("{}"))
        assertNull(OpenMeteoParser.parseCurrentWeather("""{"current":{"temperature_2m":12.0}}"""))
    }

    @Test
    fun buildsExpectedUrlWithRoundedCoordinates() {
        val url = OpenMeteoParser.buildUrl(35.689487, 139.691706)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(url.contains("latitude=35.6895"))
        assertTrue(url.contains("longitude=139.6917"))
        assertTrue(url.contains("wind_speed_unit=ms"))
    }
}
