package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GpxCourse(
    val id: String = Clock.System.now().toEpochMilliseconds().toString(),
    val name: String,
    val points: List<LocationPoint>,
    val totalDistanceMeters: Double,
    val isEnabled: Boolean = true
)

val gpxJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun List<GpxCourse>.toJson(): String = gpxJson.encodeToString(this)
fun parseGpxCoursesJson(json: String): List<GpxCourse> = try {
    gpxJson.decodeFromString<List<GpxCourse>>(json)
} catch (_: Exception) {
    emptyList()
}

object GpxImporter {
    fun sanitizeCourseName(rawName: String): String {
        var name = rawName.replace(Regex("\\.gpx$", RegexOption.IGNORE_CASE), "")
        val forbiddenChars = setOf(',', '.', '|', ':', ';', '/', '\\')
        name = name.map { if (it in forbiddenChars) '_' else it }.joinToString("")
        name = name.replace(Regex("[^a-zA-Z0-9_ -]"), "_").trim()
        if (name.length > 12) {
            name = name.take(12)
        }
        return name.ifBlank { "COURSE" }
    }

    private val NAME_REGEX = Regex("<name>(.*?)</name>", RegexOption.IGNORE_CASE)
    private val ELE_REGEX = Regex("<ele>(.*?)</ele>", RegexOption.IGNORE_CASE)
    private val TIME_REGEX = Regex("<time>(.*?)</time>", RegexOption.IGNORE_CASE)
    private val PT_REGEX = Regex("<(trkpt|rtept|wpt)\\s+([^>]*?)(?:>(.*?)</\\1>|/>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val LAT_REGEX = Regex("lat=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val LON_REGEX = Regex("lon=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /**
     * GPX形式のXML文字列をパースし、コース名、座標点リスト、総距離を抽出します。
     * trkpt (トラック), rtept (ルート), wpt (ウェイポイント) すべてに対応。
     */
    fun parseGpx(gpxContent: String): GpxCourse? = parse(gpxContent)

    fun parse(gpxContent: String): GpxCourse? {
        if (gpxContent.isBlank()) return null

        val rawName = NAME_REGEX.find(gpxContent)?.groupValues?.get(1)?.trim() ?: "COURSE"
        val name = sanitizeCourseName(rawName)
        val points = mutableListOf<LocationPoint>()
        val now = Clock.System.now()

        val matches = PT_REGEX.findAll(gpxContent)
        for (match in matches) {
            val attrs = match.groupValues[2]
            val inner = match.groupValues[3]

            val latStr = LAT_REGEX.find(attrs)?.groupValues?.get(1) ?: continue
            val lonStr = LON_REGEX.find(attrs)?.groupValues?.get(1) ?: continue

            val lat = latStr.toDoubleOrNull() ?: continue
            val lon = lonStr.toDoubleOrNull() ?: continue

            val ele = if (inner.isNotEmpty()) ELE_REGEX.find(inner)?.groupValues?.get(1)?.toDoubleOrNull() else null
            val timeStr = if (inner.isNotEmpty()) TIME_REGEX.find(inner)?.groupValues?.get(1)?.trim() else null
            val timestamp = if (timeStr != null) {
                try { Instant.parse(timeStr) } catch (_: Exception) { now }
            } else {
                now
            }

            points.add(
                LocationPoint(
                    latitude = lat,
                    longitude = lon,
                    altitude = ele,
                    timestamp = timestamp
                )
            )
        }

        if (points.isEmpty()) return null

        // 連続する点間のハバーサイン距離を合算して総距離を計算
        var totalDist = 0.0
        for (i in 1 until points.size) {
            totalDist += haversine(points[i - 1], points[i])
        }

        return GpxCourse(
            name = name,
            points = points,
            totalDistanceMeters = totalDist,
            isEnabled = true
        )
    }

    private fun haversine(p1: LocationPoint, p2: LocationPoint): Double {
        val r = 6371000.0 // 地球の半径(メートル)
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
