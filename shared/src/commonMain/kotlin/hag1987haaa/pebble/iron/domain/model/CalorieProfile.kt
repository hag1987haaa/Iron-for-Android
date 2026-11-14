package hag1987haaa.pebble.iron.domain.model

enum class BiologicalSex { MALE, FEMALE }

/** Latest body metrics read from Health Connect. Missing values stay null. */
data class HealthBodyMetrics(
    val weightKg: Float? = null,
    val heightCm: Double? = null,
    val bodyFatPct: Double? = null,
    val restingHr: Int? = null,
    val vo2Max: Double? = null,
    val bmrKcalPerDay: Double? = null,
    /** True when [restingHr] was estimated by Iron from heart-rate samples (sleep, else daytime). */
    val restingHrEstimated: Boolean = false,
)

/**
 * Body data used for calorie estimation.
 * [weightKg] is already resolved (Health Connect first, then the manual setting).
 * The optional values come from Health Connect and refine the estimate when present.
 */
data class CalorieProfile(
    val weightKg: Float,
    val sex: BiologicalSex = BiologicalSex.MALE,
    val ageYears: Int = DEFAULT_AGE_YEARS,
    val restingHr: Int? = null,
    val vo2Max: Double? = null,
    val bmrKcalPerDay: Double? = null,
    val heightCm: Double? = null,
    val bodyFatPct: Double? = null,
) {
    companion object {
        /** Used when no date of birth has been set. */
        const val DEFAULT_AGE_YEARS = 35
    }
}
