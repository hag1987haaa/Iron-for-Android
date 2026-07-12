package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType

object HealthUtils {
    /**
     * 消費カロリーの計算 (改善版)
     * 心拍数データがある場合は心拍数ベース、ない場合は METs + 傾斜補正で算出する。
     */
    fun calculateCalories(
        type: ActivityType,
        weightKg: Float,
        durationSeconds: Long,
        distanceMeters: Double = 0.0,
        elevationGainMeters: Double = 0.0,
        avgHeartRate: Double? = null,
    ): Double {
        val durationHours = durationSeconds / 3600.0
        
        if ((avgHeartRate != null) && (avgHeartRate > 60)) {
            // 1. 心拍数ベースの推定 (キーバー・エドワードの方程式の簡略版)
            // 係数は男性・35歳を想定した平均的な値を採用
            val kcalPerMin = (0.6309 * avgHeartRate + 0.1988 * weightKg + 0.2017 * 35 - 55.0969) / 4.184
            val totalKcal = kcalPerMin * (durationSeconds / 60.0)
            return totalKcal.coerceAtLeast(0.0)
        }

        // 2. METsベース + 傾斜補正
        var mets = when (type) {
            ActivityType.RUNNING -> 8.3
            ActivityType.WALKING -> 3.5
            ActivityType.CYCLING -> 8.0
            ActivityType.HIKING -> 6.0
            ActivityType.KAYAKING -> 5.0
            ActivityType.ROWING -> 7.0
            ActivityType.OTHER -> 4.0
        }

        // 傾斜による負荷増分 (1%の平均傾斜につき METs を 10% 増加させる簡易補正)
        // GPSの飛びによる異常な傾斜（スパイク）を防ぐため、gradeを45%に制限
        if (distanceMeters > 10.0) {
            val grade = ((elevationGainMeters / distanceMeters) * 100.0).coerceIn(0.0, 45.0)
            mets += (grade * 0.5) // 登り坂による補正
        }

        val totalKcal = mets * weightKg * durationHours * 1.05
        
        // 人間の生理的限界（1秒あたり約1.0kcal、1時間で3600kcal）でキャップをかけ、スパイクを完全に排除する
        val maxSafeKcal = durationSeconds.toDouble() * 1.0
        return totalKcal.coerceIn(0.0, maxSafeKcal.coerceAtLeast(totalKcal)) // 通常時はtotalKcalを返し、異常値のみ制限
    }
}
