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

    @Test
    fun testRouteWithPauseExcludesPauseTimeFromDuration() {
        // 0km: 35.000, 1000s
        // 500m: 35.0045, 1150s (移動時間 150秒)
        // 500m地点で一時停止し、300秒（5分）後に再開
        // 500m: 35.0045, 1450s (isSegmentStart = true)
        // 1km: 35.0090, 1600s (移動時間 150秒)
        // 2km: 35.0180, 1900s (移動時間 300秒, 一時停止なし)
        val p0 = LocationPoint(latitude = 35.000000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1000))
        val p1 = LocationPoint(latitude = 35.004500, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1150))
        val p2 = LocationPoint(latitude = 35.004500, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1450), isSegmentStart = true)
        val p3 = LocationPoint(latitude = 35.009000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1600))
        val p4 = LocationPoint(latitude = 35.018000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1900))

        val splits = SplitCalculator.calculateSplits(listOf(p0, p1, p2, p3, p4), isMetric = true)

        assertEquals(2, splits.size)

        // 1km目: 移動時間150s + 150s = 300秒(5分)、ポーズ時間300秒(5分)
        assertEquals(1, splits[0].splitNumber)
        assertEquals(300L, splits[0].durationSeconds)
        assertEquals(300.0, splits[0].paceSeconds, 2.0)
        assertEquals(300L, splits[0].pausedDurationSeconds)

        // 2km目: 移動時間300秒、ポーズ時間0秒
        assertEquals(2, splits[1].splitNumber)
        assertEquals(300L, splits[1].durationSeconds)
        assertEquals(300.0, splits[1].paceSeconds, 2.0)
        assertEquals(0L, splits[1].pausedDurationSeconds)
    }

    @Test
    fun testRouteWithPauseAcrossSplitBoundary() {
        // 950m 地点でポーズして 1050m 地点で再開した場合
        // 0km: 35.000000, 1000s
        // 950m: 35.008550, 1285s (移動時間 285秒)
        // 950m -> 1050m (100m区間) で一時停止、120秒後に再開 (isSegmentStart = true)
        // 1050m: 35.009450, 1405s
        // 2000m: 35.018000, 1690s (移動時間 285秒)
        val p0 = LocationPoint(latitude = 35.000000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1000))
        val p1 = LocationPoint(latitude = 35.008550, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1285))
        val p2 = LocationPoint(latitude = 35.009450, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1405), isSegmentStart = true)
        val p3 = LocationPoint(latitude = 35.018000, longitude = 135.0, timestamp = Instant.fromEpochSeconds(1690))

        val splits = SplitCalculator.calculateSplits(listOf(p0, p1, p2, p3), isMetric = true)

        assertEquals(2, splits.size)
        assertEquals(1, splits[0].splitNumber)
        assertEquals(2, splits[1].splitNumber)

        // 停止時間120秒が 950m〜1050m (1km境界を跨ぐ) で按分され、合計120秒になる
        val totalPaused = splits[0].pausedDurationSeconds + splits[1].pausedDurationSeconds
        assertEquals(120L, totalPaused)
        assertTrue(splits[0].pausedDurationSeconds > 0L)
        assertTrue(splits[1].pausedDurationSeconds > 0L)
    }
}
