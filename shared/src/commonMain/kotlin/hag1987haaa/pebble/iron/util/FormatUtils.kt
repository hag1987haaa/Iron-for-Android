package hag1987haaa.pebble.iron.util

import kotlin.math.floor

object FormatUtils {
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        } else {
            "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        }
    }

    fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        return if (km >= 10.0) {
            km.format(1)
        } else {
            km.format(2)
        }
    }

    fun formatPace(secondsPerKm: Double): String {
        if (secondsPerKm <= 0 || secondsPerKm.isInfinite()) return "0:00"
        val minutes = floor(secondsPerKm / 60.0).toInt()
        val seconds = floor(secondsPerKm % 60.0).toInt()
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    private fun Double.format(digits: Int): String {
        // Simple multiplatform decimal formatter
        val factor = 10.0.pow(digits)
        val rounded = (this * factor).toLong() / factor
        return rounded.toString()
    }

    private fun Double.pow(n: Int): Double {
        var res = 1.0
        repeat(n) { res *= this }
        return res
    }
}
