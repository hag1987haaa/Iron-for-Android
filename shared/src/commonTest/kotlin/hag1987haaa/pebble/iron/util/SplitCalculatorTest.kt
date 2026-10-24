package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitCalculatorTest {

    @Test
    fun testEmptyRoute() {
        val splits = SplitCalculator.calculateSplits(emptyList())
        assertTrue(splits.isEmpty())
    }

    @Test
    fun testSinglePointRoute() {
        val pt = LocationPoint(
            latitude = 35.0,
            longitude = 135.0,
            timestamp = Instant.fromEpochSeconds(1000)
        )
        val splits = SplitCalculator.calculateSplits(listOf(pt))
        assertTrue(splits.isEmpty())
    }

    @Test
    fun testTwoKilometersRoute() {
        // 約111km / 1度 (緯度) -> 1kmは約 0.009 度
        // 0km: 35.000, 1000s
        // 1km: 35.009, 1300s (300秒 = 5分)
        // 2km: 35.018, 1570s (270秒 = 4分30秒)
        // 2.5km: 35.0225, 1720s (150秒)
        val p0 = LocationPoint(latitude = 35.000000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1000))
        val p1 = LocationPoint(latitude = 35.009000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1300))
        val p2 = LocationPoint(latitude = 35.018000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1570))
        val p3 = LocationPoint(latitude = 35.022500, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1720))

        val splits = SplitCalculator.calculateSplits(listOf(p0, p1, p2, p3), isMetric = true)

        // 3つの区間（1km, 1km, 端数約500m）
        assertEquals(3, splits.size)
        assertEquals(1, splits[0].splitNumber)
        assertEquals(1000.0, splits[0].distanceMeters, 5.0)
        assertEquals(false, splits[0].isLastPartial)

        assertEquals(2, splits[1].splitNumber)
        assertEquals(1000.0, splits[1].distanceMeters, 5.0)
        assertEquals(false, splits[1].isLastPartial)

        assertEquals(3, splits[2].splitNumber)
        assertEquals(true, splits[2].isLastPartial)
        assertTrue(splits[2].distanceMeters > 400.0 && splits[2].distanceMeters < 600.0)
    }
}
