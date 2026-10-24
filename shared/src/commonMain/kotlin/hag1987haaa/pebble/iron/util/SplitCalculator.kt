package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.LocationPoint
import kotlin.math.roundToLong

data class SplitItem(
    val splitNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val paceSeconds: Double,
    val isLastPartial: Boolean = false,
    val avgHeartRate: Double? = null,
    val elevationGain: Double? = null
)

object SplitCalculator {
    const val METERS_PER_KM = 1000.0
    const val METERS_PER_MILE = 1609.344

    /**
     * GPSルートから指定単位（1kmまたは1マイル）ごとのスプリット（ラップタイム一覧）を計算します。
     *
     * @param route 位置情報のリスト
     * @param isMetric trueなら1km単位、falseなら1マイル単位
     * @param minPartialDistanceMeters 端数区間を表示する最小距離（メートル）。ノイズ除外用。
     * @return スプリットリスト
     */
    fun calculateSplits(
        route: List<LocationPoint>,
        isMetric: Boolean = true,
        minPartialDistanceMeters: Double = 30.0
    ): List<SplitItem> {
        if (route.size < 2) return emptyList()

        val unitMeters = if (isMetric) METERS_PER_KM else METERS_PER_MILE
        val splits = mutableListOf<SplitItem>()

        var splitNumber = 1
        var currentSplitDist = 0.0
        var currentSplitDuration = 0.0
        var currentSplitElevationGain = 0.0
        val currentSplitHeartRates = mutableListOf<Int>()

        for (i in 1 until route.size) {
            val p1 = route[i - 1]
            val p2 = route[i]

            val d = LocationUtils.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            val dtMs = (p2.timestamp.toEpochMilliseconds() - p1.timestamp.toEpochMilliseconds()).coerceAtLeast(0L)
            val dt = dtMs / 1000.0

            val alt1 = p1.altitude
            val alt2 = p2.altitude
            val stepElev = if (alt1 != null && alt2 != null && alt2 > alt1) alt2 - alt1 else 0.0

            p2.heartRate?.let { currentSplitHeartRates.add(it) }

            // 今回の区間を進める
            var remainingD = d
            var remainingDt = dt
            var remainingElev = stepElev

            while (currentSplitDist + remainingD >= unitMeters) {
                val neededD = unitMeters - currentSplitDist
                val ratio = if (remainingD > 0.0) (neededD / remainingD).coerceIn(0.0, 1.0) else 1.0
                val consumedDt = remainingDt * ratio
                val consumedElev = remainingElev * ratio

                val totalDuration = currentSplitDuration + consumedDt
                val totalElev = currentSplitElevationGain + consumedElev
                val avgHr = if (currentSplitHeartRates.isNotEmpty()) currentSplitHeartRates.average() else null

                val durationSec = totalDuration.roundToLong()
                val paceSec = totalDuration // unitMeters あたりの所要時間なのでそのまま paceSec となる

                splits.add(
                    SplitItem(
                        splitNumber = splitNumber++,
                        distanceMeters = unitMeters,
                        durationSeconds = durationSec,
                        paceSeconds = paceSec,
                        isLastPartial = false,
                        avgHeartRate = avgHr,
                        elevationGain = if (totalElev > 0.0) totalElev else null
                    )
                )

                // 次のスプリット用に残りを更新
                remainingD -= neededD
                remainingDt -= consumedDt
                remainingElev -= consumedElev

                currentSplitDist = 0.0
                currentSplitDuration = 0.0
                currentSplitElevationGain = 0.0
                currentSplitHeartRates.clear()
                p2.heartRate?.let { currentSplitHeartRates.add(it) }
            }

            currentSplitDist += remainingD
            currentSplitDuration += remainingDt
            currentSplitElevationGain += remainingElev
        }

        // 最後の端数区間がある場合
        if (currentSplitDist >= minPartialDistanceMeters && currentSplitDuration > 0.0) {
            val durationSec = currentSplitDuration.roundToLong()
            val ratioToUnit = currentSplitDist / unitMeters
            val paceSec = if (ratioToUnit > 0.0) currentSplitDuration / ratioToUnit else 0.0
            val avgHr = if (currentSplitHeartRates.isNotEmpty()) currentSplitHeartRates.average() else null

            splits.add(
                SplitItem(
                    splitNumber = splitNumber,
                    distanceMeters = currentSplitDist,
                    durationSeconds = durationSec,
                    paceSeconds = paceSec,
                    isLastPartial = true,
                    avgHeartRate = avgHr,
                    elevationGain = if (currentSplitElevationGain > 0.0) currentSplitElevationGain else null
                )
            )
        }

        return splits
    }
}
