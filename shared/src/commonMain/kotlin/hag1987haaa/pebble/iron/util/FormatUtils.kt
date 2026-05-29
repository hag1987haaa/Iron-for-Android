package hag1987haaa.pebble.iron.util

import kotlin.math.roundToInt

object FormatUtils {
    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val mm = if (m < 10) "0$m" else "$m"
        val ss = if (s < 10) "0$s" else "$s"
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }

    fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        val integerPart = km.toInt()
        val fractionalPart = ((km - integerPart) * 100).toInt()
        val ff = if (fractionalPart < 10) "0$fractionalPart" else "$fractionalPart"
        return "$integerPart.$ff"
    }

    fun formatPace(secondsPerKm: Double): String {
        if (secondsPerKm <= 0 || secondsPerKm.isInfinite()) return "0:00"
        val pace = secondsPerKm.roundToInt()
        val m = pace / 60
        val s = pace % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
