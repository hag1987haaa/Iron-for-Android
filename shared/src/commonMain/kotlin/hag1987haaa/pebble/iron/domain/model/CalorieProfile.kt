package hag1987haaa.pebble.iron.domain.model

enum class BiologicalSex { MALE, FEMALE }

/**
 * Body data used for calorie estimation.
 * The optional values refine the estimate when known; null falls back to population defaults.
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
