package hag1987haaa.pebble.iron.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestingHrEstimatorTest {
    private fun night(base: Int, n: Int = 60) = List(n) { base + (it % 10) } // base..base+9

    @Test
    fun medianOfNightlyLows() {
        val nights = listOf(night(50), night(52), night(70)) // one bad night
        // 10th percentile of base..base+9 ≈ base+0.9 -> lows 50.9, 52.9, 70.9 -> median 52.9
        assertEquals(53, RestingHrEstimator.estimate(nights, 0.10, 10))
    }

    @Test
    fun ignoresArtifactsAndShortNights() {
        val nights = listOf(
            night(55) + listOf(0, 5, 250), // dropout/artifact values are filtered
            night(57),
            List(5) { 40 },               // too few samples: ignored
        )
        assertEquals(57, RestingHrEstimator.estimate(nights, 0.10, 10))
    }

    @Test
    fun needsAtLeastTwoPeriods() {
        assertNull(RestingHrEstimator.estimate(listOf(night(55)), 0.10, 10))
        assertNull(RestingHrEstimator.estimate(emptyList(), 0.10, 10))
    }

    @Test
    fun percentileInterpolates() {
        assertEquals(2.5, RestingHrEstimator.percentile(listOf(1, 2, 3, 4), 0.5))
        assertEquals(1.0, RestingHrEstimator.percentile(listOf(4, 3, 2, 1), 0.0))
    }
}
