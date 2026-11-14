package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.BiologicalSex
import hag1987haaa.pebble.iron.domain.model.CalorieProfile

object HealthUtils {
    /**
     * Active calories (kcal) for a GPS workout, or for one segment of it.
     *
     * - Movement estimate: METs from GPS speed for the activity, plus a hill (grade) correction.
     * - Heart rate clearly above resting: Keytel et al. (2005); the VO2max variant is used when
     *   Health Connect has a VO2max record. Between "resting + 30" and "+50" bpm the two estimates
     *   are blended, because the HR equations are unreliable near resting heart rate.
     * - Resting energy (Health Connect BMR, else Katch-McArdle / Mifflin-St Jeor, else 1 kcal/kg/h)
     *   is subtracted, so the result is active calories, matching ActiveCaloriesBurnedRecord.
     */
    fun calculateCalories(
        type: ActivityType,
        profile: CalorieProfile,
        durationSeconds: Long,
        distanceMeters: Double = 0.0,
        elevationGainMeters: Double = 0.0,
        avgHeartRate: Double? = null,
    ): Double {
        if (durationSeconds <= 0) return 0.0
        val minutes = durationSeconds / 60.0

        val restingPerMin = restingKcalPerMin(profile)
        val movementPerMin = movementKcalPerMin(type, profile.weightKg, durationSeconds, distanceMeters, elevationGainMeters)

        val grossPerMin = if (avgHeartRate != null && avgHeartRate > 0) {
            val (low, high) = hrThresholds(profile)
            val w = ((avgHeartRate - low) / (high - low)).coerceIn(0.0, 1.0)
            w * keytelKcalPerMin(profile, avgHeartRate) + (1 - w) * movementPerMin
        } else {
            movementPerMin
        }

        val activeKcal = (grossPerMin - restingPerMin).coerceAtLeast(0.0) * minutes
        // 人間の生理的限界（1秒あたり約1.0kcal）でキャップをかけ、GPSスパイク等を排除する
        return activeKcal.coerceIn(0.0, durationSeconds.toDouble())
    }

    /** Resting energy per day: Health Connect BMR, else Katch-McArdle, else Mifflin-St Jeor, else 24 kcal/kg. */
    fun bmrKcalPerDay(p: CalorieProfile): Double {
        p.bmrKcalPerDay?.takeIf { it in 800.0..4500.0 }?.let { return it }
        p.bodyFatPct?.takeIf { it in 3.0..60.0 }?.let { bf ->
            return 370 + 21.6 * p.weightKg * (1 - bf / 100.0)
        }
        p.heightCm?.takeIf { it in 100.0..250.0 }?.let { h ->
            val base = 10 * p.weightKg + 6.25 * h - 5 * p.ageYears
            return if (p.sex == BiologicalSex.MALE) base + 5 else base - 161
        }
        return 24.0 * p.weightKg // 1 MET ≈ 1 kcal/kg/h
    }

    fun restingKcalPerMin(p: CalorieProfile): Double = bmrKcalPerDay(p) / 1440.0

    /** Gross kcal/min from Keytel et al. (2005). */
    fun keytelKcalPerMin(p: CalorieProfile, hr: Double): Double {
        val a = p.ageYears.toDouble()
        val w = p.weightKg.toDouble()
        val vo2 = p.vo2Max?.takeIf { it in 15.0..95.0 }
        val kJ = when {
            vo2 != null && p.sex == BiologicalSex.MALE -> -95.7735 + 0.634 * hr + 0.404 * vo2 + 0.394 * w + 0.271 * a
            vo2 != null -> -59.3954 + 0.45 * hr + 0.380 * vo2 + 0.103 * w + 0.274 * a
            p.sex == BiologicalSex.MALE -> -55.0969 + 0.6309 * hr + 0.1988 * w + 0.2017 * a
            else -> -20.4022 + 0.4472 * hr - 0.1263 * w + 0.074 * a
        }
        return kJ / 4.184
    }

    /** Below `low` bpm only the movement estimate is used; above `high` only Keytel. */
    fun hrThresholds(p: CalorieProfile): Pair<Double, Double> {
        val low = (p.restingHr?.takeIf { it in 30..110 }?.let { it + 30.0 } ?: 90.0).coerceIn(80.0, 105.0)
        return low to low + 20.0
    }

    /** Gross kcal/min from GPS speed and hill grade (METs × kg). */
    fun movementKcalPerMin(
        type: ActivityType,
        weightKg: Float,
        durationSeconds: Long,
        distanceMeters: Double,
        elevationGainMeters: Double,
    ): Double {
        val speedKmh = (distanceMeters / 1000.0) / (durationSeconds / 3600.0)

        val walkingMets = when {
            speedKmh < 2.0 -> 2.0
            speedKmh < 8.0 -> 0.5 * speedKmh + 1.5 // 4km/h->3.5, 6km/h->4.5
            else -> 5.5
        }
        var mets = when (type) {
            ActivityType.RUNNING -> when {
                speedKmh < 6.0 -> 6.0
                speedKmh < 20.0 -> 0.75 * speedKmh + 2.3 // 8km/h->8.3, 12km/h->11.3
                else -> 16.0
            }
            ActivityType.WALKING -> walkingMets
            // 不整地は舗装路より約20%負荷が高い (Pandolf の地形係数)
            ActivityType.HIKING -> (walkingMets * 1.2).coerceAtLeast(4.0)
            ActivityType.CYCLING -> when {
                speedKmh < 15.0 -> 6.0
                speedKmh < 30.0 -> 0.4 * speedKmh // 20km/h->8.0, 25km/h->10.0
                else -> 12.0
            }
            ActivityType.KAYAKING -> 5.0
            ActivityType.ROWING -> 7.0
            ActivityType.OTHER -> 4.0
        }

        // 登り坂による補正: 平均傾斜1%につき +0.5 METs。
        // GPSの飛びによる異常値を防ぐため grade は 45% に制限
        if (distanceMeters > 10.0) {
            val grade = ((elevationGainMeters / distanceMeters) * 100.0).coerceIn(0.0, 45.0)
            mets += grade * 0.5
        }

        return mets * weightKg * 1.05 / 60.0
    }
}
