package hag1987haaa.pebble.iron.pebble

import android.util.Log
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.util.HealthUtils
import hag1987haaa.pebble.iron.util.LocationUtils
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.min

object GraphDataGenerator {

    private const val MAX_DATA_POINTS = 45
    private const val MAX_SAFE_CHAR_LENGTH = 200 // Pebble's AppMessage buffer safety limit
    private const val VALUE_CLIP_MAX = 9999 

    fun generateUnifiedGraph(stats: RunStatistics, typeId: Int, settings: AppSettings): String {
        return try {
            // 1. データとスケールの生成
            val (dataPart, xScaleLabel) = if (typeId == 0) {
                // 距離ベース（ペース/速度）
                if (stats.route.isEmpty() || stats.totalDistanceMeters < 10.0) {
                    "0" to "X:0${if(settings.isMetric)"m" else "ft"}"
                } else {
                    val result = generateDistanceBasedGraphDataWithScale(stats)
                    val scale = result.first
                    val unit = if (settings.isMetric) "m" else "ft"
                    val label = if (scale >= 1000) "X:${scale/1000}${if(settings.isMetric)"km" else "mi"}" else "X:${scale}${unit}"
                    result.second to label
                }
            } else {
                // 時間ベース
                val totalSeconds = stats.totalSeconds
                if (stats.route.isEmpty() || totalSeconds <= 0) {
                    "0" to "X:0min"
                } else {
                    val scaleMinutes = ceil(totalSeconds.toDouble() / 60.0 / 40.0).toInt().coerceAtLeast(1)
                    val data = generateTimeBasedGraphDataOnly(stats, typeId, scaleMinutes, settings)
                    data to "X:${scaleMinutes}min"
                }
            }

            // 2. ラベル部分の組み立て (短縮形)
            val labelInfo = when (typeId) {
                0 -> { // Pace / Speed
                    val unit = if (settings.isMetric) "/km" else "/mi"
                    "PACE($unit),$xScaleLabel,MAX,MIN"
                }
                1 -> { // Distance
                    val unit = if (settings.isMetric) "km" else "mi"
                    "DIST($unit),$xScaleLabel,${stats.formattedDistance},0"
                }
                2 -> { // Steps
                    "STEPS,$xScaleLabel,${stats.steps},0"
                }
                3 -> { // Altitude
                    val max = stats.route.mapNotNull { it.altitude }.maxOrNull()?.roundToInt() ?: 0
                    val min = stats.route.mapNotNull { it.altitude }.minOrNull()?.roundToInt() ?: 0
                    "ALT,$xScaleLabel,${max}m,${min}m"
                }
                4 -> { // Heart Rate
                    val max = stats.heartRates.maxOrNull() ?: 0
                    val min = stats.heartRates.minOrNull() ?: 0
                    "HR,$xScaleLabel,${max}bpm,${min}bpm"
                }
                5 -> { // Calories
                    "CAL,$xScaleLabel,${stats.calories.toInt()}kcal,0"
                }
                else -> "DATA,$xScaleLabel,MAX,MIN"
            }

            val fullCsv = "$typeId,$labelInfo,$dataPart"
            enforceLengthLimit(fullCsv)
        } catch (e: Exception) {
            Log.e("GraphGenerator", "Fatal error in generation", e)
            "$typeId,ERROR,X,0,0,0"
        }
    }

    private fun generateTimeBasedGraphDataOnly(stats: RunStatistics, typeId: Int, scaleMinutes: Int, settings: AppSettings): String {
        val totalSeconds = stats.totalSeconds
        if (stats.route.isEmpty()) return "0"
        
        val bucketSizeMs = scaleMinutes * 60 * 1000L
        val startInstant = stats.startTime ?: return "0"

        val startTime = startInstant.toEpochMilliseconds()
        val numBuckets = (totalSeconds * 1000 / bucketSizeMs).toInt() + 1
        val bucketValues = DoubleArray(numBuckets)
        val bucketCounts = IntArray(numBuckets)

        for (i in 1 until stats.route.size) {
            val p1 = stats.route[i - 1]
            val p2 = stats.route[i]
            val t1 = p1.timestamp.toEpochMilliseconds()
            val t2 = p2.timestamp.toEpochMilliseconds()
            if (t2 <= t1) continue

            val valDiff: Double
            val isSumType: Boolean

            when (typeId) {
                1 -> { valDiff = LocationUtils.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude); isSumType = true }
                2 -> { valDiff = ((p2.steps ?: 0) - (p1.steps ?: 0)).toDouble(); isSumType = true }
                3 -> { valDiff = (p2.altitude ?: p1.altitude ?: 0.0); isSumType = false }
                4 -> { valDiff = (p2.heartRate ?: p1.heartRate ?: 0).toDouble(); isSumType = false }
                5 -> { 
                    val duration = (p2.timestamp.epochSeconds - p1.timestamp.epochSeconds).coerceAtLeast(1)
                    val dist = LocationUtils.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    valDiff = HealthUtils.calculateCalories(
                        type = stats.activityType,
                        weightKg = settings.userWeightKg,
                        durationSeconds = duration,
                        distanceMeters = dist,
                        avgHeartRate = p2.heartRate?.toDouble()
                    )
                    isSumType = true 
                }
                else -> return "0"
            }

            var t = t1
            while (t < t2) {
                val bucketIdx = ((t - startTime) / bucketSizeMs).toInt()
                if (bucketIdx < 0 || bucketIdx >= numBuckets) { t = ((bucketIdx + 1) * bucketSizeMs + startTime).coerceAtLeast(t + 1); continue }
                val nextBucketStart = (bucketIdx + 1) * bucketSizeMs + startTime
                val overlapEnd = min(t2, nextBucketStart)
                val duration = overlapEnd - t
                val totalDuration = t2 - t1
                
                if (isSumType) { bucketValues[bucketIdx] += valDiff * (duration.toDouble() / totalDuration) }
                else { bucketValues[bucketIdx] += valDiff * duration; bucketCounts[bucketIdx] += duration.toInt() }
                t = overlapEnd
            }
        }

        val resultData = mutableListOf<Int>()
        for (i in 0 until numBuckets) {
            val v = if (typeId == 3 || typeId == 4) {
                if (bucketCounts[i] > 0) bucketValues[i] / bucketCounts[i] else 0.0
            } else if (typeId == 2) {
                bucketValues[i] / scaleMinutes.coerceAtLeast(1)
            } else {
                bucketValues[i]
            }
            
            // NaN や Infinity を 0.0 に安全にフォールバック
            val safeV = if (v.isFinite()) v else 0.0
            resultData.add(safeV.roundToInt().coerceIn(0, VALUE_CLIP_MAX))
        }

        return resultData.takeLast(MAX_DATA_POINTS).joinToString(",")
    }

    private fun generateDistanceBasedGraphDataWithScale(stats: RunStatistics): Pair<Int, String> {
        val totalDist = stats.totalDistanceMeters
        val scaleValue: Int
        val bucketStepMeters: Double
        
        if (totalDist <= 8000.0) { scaleValue = 200; bucketStepMeters = 200.0 }
        else if (totalDist <= 20000.0) { scaleValue = 500; bucketStepMeters = 500.0 }
        else {
            val scaleKm = ceil((totalDist / 1000.0) / 40.0).toInt().coerceAtLeast(1)
            scaleValue = scaleKm * 1000
            bucketStepMeters = scaleKm * 1000.0
        }

        if (stats.route.isEmpty()) return scaleValue to "0"

        val buckets = mutableListOf<Int>()
        var currentBucketDist = 0.0
        var currentBucketStartTime = stats.route.first().timestamp.toEpochMilliseconds()
        
        var prevLoc = stats.route.first()
        for (i in 1 until stats.route.size) {
            val currLoc = stats.route[i]
            val d = LocationUtils.calculateDistance(prevLoc.latitude, prevLoc.longitude, currLoc.latitude, currLoc.longitude)
            // NaN/Infinity チェックを追加
            val validD = if (d.isFinite() && d > 0.0) d else 0.0
            
            if (currentBucketDist + validD < bucketStepMeters) { 
                currentBucketDist += validD 
            } else {
                val currTime = currLoc.timestamp.toEpochMilliseconds()
                val durationSec = (currTime - currentBucketStartTime) / 1000
                // 負の値や異常値をガード
                val safeDuration = durationSec.coerceAtLeast(0).toInt()
                buckets.add(safeDuration.coerceIn(0, VALUE_CLIP_MAX))
                
                // 次のバケットへの繰越距離（異常に大きな移動を考慮してクランプ）
                currentBucketDist = ((currentBucketDist + validD) - bucketStepMeters).coerceAtLeast(0.0)
                currentBucketStartTime = currTime
            }
            prevLoc = currLoc
        }
        val lastTime = stats.route.last().timestamp.toEpochMilliseconds()
        buckets.add(((lastTime - currentBucketStartTime) / 1000).toInt().coerceIn(0, VALUE_CLIP_MAX))

        return scaleValue to buckets.takeLast(MAX_DATA_POINTS).joinToString(",")
    }

    private fun enforceLengthLimit(csv: String): String {
        if (csv.length <= MAX_SAFE_CHAR_LENGTH) return csv
        
        val parts = csv.split(",").toMutableList()
        // ヘッダー(0:typeId, 1-4:ラベル関連)を維持し、古いデータ(index 5)から削除
        while (parts.size > 5 && (parts.joinToString(",").length > MAX_SAFE_CHAR_LENGTH)) {
            parts.removeAt(5)
        }
        return parts.joinToString(",")
    }
}
