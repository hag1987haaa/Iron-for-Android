package hag1987haaa.pebble.iron.util

import kotlin.math.roundToInt

/**
 * Estimates resting heart rate from heart-rate samples, for when no app writes a
 * RestingHeartRateRecord to Health Connect.
 *
 * Each period is one night of sleep (or one day when no sleep data exists). For each period
 * we take a low percentile of its samples, which ignores single dropouts/artifacts, then use
 * the median across periods so one bad night doesn't skew the result.
 */
object RestingHrEstimator {
    const val SLEEP_PERCENTILE = 0.10
    const val SLEEP_MIN_SAMPLES = 10
    const val DAY_PERCENTILE = 0.05
    const val DAY_MIN_SAMPLES = 30
    const val MIN_PERIODS = 2

    fun estimate(
        periods: List<List<Int>>,
        percentile: Double,
        minSamplesPerPeriod: Int,
        minPeriods: Int = MIN_PERIODS,
    ): Int? {
        val lows = periods
            .map { samples -> samples.filter { it in 25..220 } }
            .filter { it.size >= minSamplesPerPeriod }
            .map { percentile(it, percentile) }
        if (lows.size < minPeriods) return null
        return percentile(lows, 0.5).roundToInt().takeIf { it in 30..110 }
    }

    /** Linear-interpolated percentile, p in 0..1. */
    internal fun percentile(values: List<Number>, p: Double): Double {
        val sorted = values.map { it.toDouble() }.sorted()
        if (sorted.size == 1) return sorted[0]
        val pos = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, sorted.lastIndex)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }
}
