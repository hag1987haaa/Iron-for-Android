package hag1987haaa.pebble.iron.weather

import hag1987haaa.pebble.iron.domain.weather.OpenMeteoParser
import hag1987haaa.pebble.iron.domain.weather.WeatherFetcher
import hag1987haaa.pebble.iron.domain.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class OpenMeteoWeatherFetcher : WeatherFetcher {
    override suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(OpenMeteoParser.buildUrl(latitude, longitude))
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "IronForPebble/Android")
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                OpenMeteoParser.parseCurrentWeather(body)
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
}
