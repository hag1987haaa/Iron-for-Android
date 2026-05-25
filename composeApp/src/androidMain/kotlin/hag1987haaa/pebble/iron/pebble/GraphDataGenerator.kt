package hag1987haaa.pebble.iron.pebble

import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.util.LocationUtils
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.min

object GraphDataGenerator {

    private const val MAX_DATA_POINTS = 45
    private const val MAX_SAFE_CHAR_LENGTH = 200 // Pebble's AppMessage buffer safety limit
    private const val VALUE_CLIP_MAX = 9999 

    /**
     * ハイブリッド形式のグラフデータを生成
     * format: "{graph_type_id},{scale_value},{data1},{data2},..."
     */
    fun generateUnifiedGraph(stats: RunStatistics, typeId: Int): String {
        return try {
            val rawCsv = when (typeId) {
                0 -> generateDistanceBasedGraphData(stats)
                1, 2, 3, 4 -> generateTimeBasedGraphData(stats, typeId)
                else -> "0,1,0"
            }
            enforceLengthLimit(rawCsv)
        } catch (e: Exception) {
            android.util.Log.e("GraphGenerator", "Fatal error in generation", e)
            "$typeId,1,0" // 最小限の安全なデータを返す
        }
    }

    private fun enforceLengthLimit(csv: String): String {
        if (csv.length <= MAX_SAFE_CHAR_LENGTH) return csv
        
        val parts = csv.split(",").toMutableList()
        // typeId と scale (parts[0], [1]) は絶対に維持し、古いデータ(前方)から削除
        while (parts.size > 3 && (parts.joinToString(",").length > MAX_SAFE_CHAR_LENGTH)) {
            parts.removeAt(2) 
        }
        return parts.joinToString(",")
    }

    private fun generateTimeBasedGraphData(stats: RunStatistics, typeId: Int): String {
        val totalSeconds = stats.totalSeconds
        val scaleMinutes = ceil(totalSeconds.toDouble() / 60.0 / 40.0).toInt().coerceAtLeast(1)
        val bucketSizeMs = scaleMinutes * 60 * 1000L
        
        val startInstant = stats.startTime ?: return "$typeId,$scaleMinutes,0"
        if (stats.route.isEmpty()) return "$typeId,$scaleMinutes,0"

        val startTime = startInstant.toEpochMilliseconds()
        val numBuckets = (totalSeconds * 1000 / bucketSizeMs).toInt() + 1
        val bucketValues = DoubleArray(numBuckets)
        val bucketCounts = IntArray(numBuckets)

        // 全ルート地点をスキャンして各バケットに値を分配
        for (i in 1 until stats.route.size) {
            val p1 = stats.route[i - 1]
            val p2 = stats.route[i]
            val t1 = p1.timestamp.toEpochMilliseconds()
            val t2 = p2.timestamp.toEpochMilliseconds()
            if (t2 <= t1) continue

            val valDiff: Double
            val isSumType: Boolean // DISTやSTEPは合計、ALTやHRは平均

            when (typeId) {
                1 -> { // DIST: 合計距離(m)
                    valDiff = LocationUtils.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    isSumType = true
                }
                2 -> { // STEP: SPM (累積歩数から算出)
                    valDiff = ((p2.steps ?: 0) - (p1.steps ?: 0)).toDouble()
                    isSumType = true
                }
                3 -> { // ALT: 高度(m)
                    valDiff = (p2.altitude ?: p1.altitude ?: 0.0)
                    isSumType = false
                }
                4 -> { // HR: 心拍数(BPM)
                    valDiff = (p2.heartRate ?: p1.heartRate ?: 0).toDouble()
                    isSumType = false
                }
                else -> return "$typeId,$scaleMinutes,0"
            }

            var t = t1
            while (t < t2) {
                val bucketIdx = ((t - startTime) / bucketSizeMs).toInt()
                if (bucketIdx < 0 || bucketIdx >= numBuckets) {
                    t = ((bucketIdx + 1) * bucketSizeMs + startTime).coerceAtLeast(t + 1)
                    continue
                }
                val nextBucketStart = (bucketIdx + 1) * bucketSizeMs + startTime
                val overlapEnd = min(t2, nextBucketStart)
                val duration = overlapEnd - t
                val totalDuration = t2 - t1
                
                if (isSumType) {
                    // 距離や歩数は、時間に応じてバケットに比例分配（これが補間ロジックになります）
                    bucketValues[bucketIdx] += valDiff * (duration.toDouble() / totalDuration)
                } else {
                    // 高度や心拍数は、滞在時間による加重平均
                    bucketValues[bucketIdx] += valDiff * duration
                    bucketCounts[bucketIdx] += duration.toInt()
                }
                t = overlapEnd
            }
        }

        val resultData = mutableListOf<Int>()
        for (i in 0 until numBuckets) {
            val v = if (typeId == 3 || typeId == 4) {
                if (bucketCounts[i] > 0) bucketValues[i] / bucketCounts[i] else 0.0
            } else if (typeId == 2) {
                // bucketValues[i] はその区間の歩数。SPMにするには 1分あたりの値に変換
                bucketValues[i] / scaleMinutes 
            } else {
                bucketValues[i]
            }
            resultData.add(v.roundToInt().coerceIn(0, VALUE_CLIP_MAX))
        }

        val data = resultData.takeLast(MAX_DATA_POINTS)
        return "$typeId,$scaleMinutes,${data.joinToString(",")}"
    }

    private fun generateDistanceBasedGraphData(stats: RunStatistics): String {
        val totalDist = stats.totalDistanceMeters
        val scaleValue: Int
        val bucketStepMeters: Double
        
        // 距離に応じた固定ステップ(200m/500m)または動的スケール(km)
        if (totalDist <= 8000.0) {
            scaleValue = 200
            bucketStepMeters = 200.0
        } else if (totalDist <= 20000.0) {
            scaleValue = 500
            bucketStepMeters = 500.0
        } else {
            val scaleKm = ceil((totalDist / 1000.0) / 40.0).toInt().coerceAtLeast(1)
            scaleValue = scaleKm
            bucketStepMeters = scaleKm * 1000.0
        }

        if (stats.route.isEmpty()) return "0,$scaleValue,0"

        val buckets = mutableListOf<Int>()
        var currentBucketDist = 0.0
        var currentBucketStartTime = stats.route.first().timestamp.toEpochMilliseconds()
        
        var prevLoc = stats.route.first()
        for (i in 1 until stats.route.size) {
            val currLoc = stats.route[i]
            val d = LocationUtils.calculateDistance(prevLoc.latitude, prevLoc.longitude, currLoc.latitude, currLoc.longitude)
            val validD = if (d.isFinite() && d > 0) d else 0.0
            
            if (currentBucketDist + validD < bucketStepMeters) {
                currentBucketDist += validD
            } else {
                val currTime = currLoc.timestamp.toEpochMilliseconds()
                val durationSec = (currTime - currentBucketStartTime) / 1000
                buckets.add(durationSec.toInt().coerceIn(0, VALUE_CLIP_MAX))
                
                currentBucketDist = (currentBucketDist + validD) - bucketStepMeters
                currentBucketStartTime = currTime
            }
            prevLoc = currLoc
        }
        
        // 最後の不完全なバケット
        val lastTime = stats.route.last().timestamp.toEpochMilliseconds()
        val finalDuration = (lastTime - currentBucketStartTime) / 1000
        buckets.add(finalDuration.toInt().coerceIn(0, VALUE_CLIP_MAX))

        val data = buckets.takeLast(MAX_DATA_POINTS)
        return "0,$scaleValue,${data.joinToString(",")}"
    }
}
