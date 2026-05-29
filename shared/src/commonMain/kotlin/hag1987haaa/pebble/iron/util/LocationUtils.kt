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

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
