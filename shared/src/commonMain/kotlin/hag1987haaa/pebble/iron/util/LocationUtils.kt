package hag1987haaa.pebble.iron.util

import kotlin.math.*

object LocationUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the distance between two points in meters using the Haversine formula.
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = (lat2 - lat1).toRadians()
        val dLon = (lon2 - lon1).toRadians()

        val a = (sin(dLat / 2).pow(2.0) +
                cos(lat1.toRadians()) * cos(lat2.toRadians()) *
                sin(dLon / 2).pow(2.0))

        // 浮動小数点の誤差で a が 1.0 を僅かに超える場合があるため、coerceIn でガード
        val safeA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(safeA), sqrt(1.0 - safeA))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * 2点間の方位を計算する (北=0, 東=90, 南=180, 西=270)
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val phi1 = lat1.toRadians()
        val phi2 = lat2.toRadians()
        val lambda1 = lon1.toRadians()
        val lambda2 = lon2.toRadians()

        val y = sin(lambda2 - lambda1) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(lambda2 - lambda1)
        
        val bearing = atan2(y, x)
        return (bearing.toDegrees() + 360.0) % 360.0
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
