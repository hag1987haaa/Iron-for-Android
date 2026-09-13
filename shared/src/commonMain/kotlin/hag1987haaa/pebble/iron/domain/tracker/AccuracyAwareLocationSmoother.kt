package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlin.math.pow

internal class AccuracyAwareLocationSmoother {
    fun smooth(window: List<LocationPoint>): LocationPoint {
        require(window.isNotEmpty())

        val latest = window.last()
        var totalWeight = 0.0
        var latitudeSum = 0.0
        var longitudeSum = 0.0
        var altitudeSum = 0.0
        var recencyWeightTotal = 0.0
        val usableAccuracies = window.map { point ->
            point.accuracy?.takeIf { it.isFinite() && it >= 0.0 }
        }
        val useAccuracy = usableAccuracies.all { it != null }

        window.forEachIndexed { index, point ->
            val recencyWeight = (index + 1).toDouble().pow(2.0)
            val accuracyWeight = if (useAccuracy) {
                1.0 / usableAccuracies[index]!!.coerceAtLeast(1.0).pow(2.0)
            } else {
                1.0
            }
            val weight = recencyWeight * accuracyWeight

            latitudeSum += point.latitude * weight
            longitudeSum += point.longitude * weight
            totalWeight += weight
            altitudeSum += (point.altitude ?: 0.0) * recencyWeight
            recencyWeightTotal += recencyWeight
        }

        return latest.copy(
            latitude = latitudeSum / totalWeight,
            longitude = longitudeSum / totalWeight,
            altitude = if (latest.altitude != null) {
                altitudeSum / recencyWeightTotal
            } else {
                null
            },
        )
    }
}
