package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.BiologicalSex
import hag1987haaa.pebble.iron.domain.model.CalorieProfile
import hag1987haaa.pebble.iron.domain.model.HealthBodyMetrics
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthUtilsTest {
    private fun near(expected: Double, actual: Double, tol: Double = 0.5) =
        assertTrue(abs(expected - actual) < tol, "expected ~$expected but was $actual")

    private val me = CalorieProfile(80f, BiologicalSex.MALE, 39)

    @Test
    fun keytelMaleUsesAgeAndSubtractsResting() {
        // 39y male, 80 kg, 150 bpm (well above blend range), 30 min
        val kcal = HealthUtils.calculateCalories(ActivityType.RUNNING, me, 1800, avgHeartRate = 150.0)
        val gross = (-55.0969 + 0.6309 * 150 + 0.1988 * 80 + 0.2017 * 39) / 4.184 * 30
        val resting = 24.0 * 80 / 1440 * 30
        near(gross - resting, kcal)
    }

    @Test
    fun keytelFemaleFormula() {
        val p = CalorieProfile(60f, BiologicalSex.FEMALE, 30)
        near((-20.4022 + 0.4472 * 150 - 0.1263 * 60 + 0.074 * 30) / 4.184 - 24.0 * 60 / 1440,
            HealthUtils.calculateCalories(ActivityType.RUNNING, p, 60, avgHeartRate = 150.0), 0.05)
    }

    @Test
    fun vo2MaxVariantUsedWhenAvailable() {
        val fit = me.copy(vo2Max = 55.0)
        val expected = (-95.7735 + 0.634 * 150 + 0.404 * 55 + 0.394 * 80 + 0.271 * 39) / 4.184
        near(expected, HealthUtils.keytelKcalPerMin(fit, 150.0), 0.01)
    }

    @Test
    fun lowHeartRateFallsBackToMovementEstimate() {
        // HR 85 with resting 60 -> below resting+30, so GPS speed estimate only
        val p = me.copy(restingHr = 60)
        val withHr = HealthUtils.calculateCalories(ActivityType.WALKING, p, 1800, 2500.0, avgHeartRate = 85.0)
        val noHr = HealthUtils.calculateCalories(ActivityType.WALKING, p, 1800, 2500.0)
        near(noHr, withHr, 0.01)
    }

    @Test
    fun blendIsBetweenEstimates() {
        val p = me.copy(restingHr = 60) // blend 90..110 bpm
        val movement = HealthUtils.movementKcalPerMin(ActivityType.WALKING, 80f, 1800, 2500.0, 0.0)
        val keytel = HealthUtils.keytelKcalPerMin(p, 100.0)
        val resting = HealthUtils.restingKcalPerMin(p)
        val mid = HealthUtils.calculateCalories(ActivityType.WALKING, p, 1800, 2500.0, avgHeartRate = 100.0)
        near(((movement + keytel) / 2 - resting) * 30, mid, 0.01)
    }

    @Test
    fun hikingUsesSpeedAndGrade() {
        val flat = HealthUtils.calculateCalories(ActivityType.HIKING, me, 3600, 4000.0)
        val hill = HealthUtils.calculateCalories(ActivityType.HIKING, me, 3600, 4000.0, elevationGainMeters = 400.0)
        val walk = HealthUtils.calculateCalories(ActivityType.WALKING, me, 3600, 4000.0)
        assertTrue(hill > flat * 2, "10% grade should add a lot: flat=$flat hill=$hill")
        assertTrue(flat > walk, "trail should cost more than pavement")
    }

    @Test
    fun bmrSourcePriority() {
        assertEquals(1800.0, HealthUtils.bmrKcalPerDay(me.copy(bmrKcalPerDay = 1800.0, bodyFatPct = 20.0)))
        near(370 + 21.6 * 64, HealthUtils.bmrKcalPerDay(me.copy(bodyFatPct = 20.0, heightCm = 180.0)), 0.01)
        near(10 * 80 + 6.25 * 180 - 5 * 39 + 5.0, HealthUtils.bmrKcalPerDay(me.copy(heightCm = 180.0)), 0.01)
        near(24.0 * 80, HealthUtils.bmrKcalPerDay(me), 0.01)
    }

    @Test
    fun capIsEnforced() {
        // Absurd grade spike for 10 s must not exceed 1 kcal/s
        val kcal = HealthUtils.calculateCalories(
            ActivityType.RUNNING, CalorieProfile(300f), 10, distanceMeters = 50.0, elevationGainMeters = 100.0
        )
        assertEquals(10.0, kcal)
    }

    @Test
    fun healthConnectMetricsFlowIntoProfile() {
        val s = AppSettings().apply { userWeightKg = 70f }
        assertEquals(70f, s.calorieProfile().weightKg)
        s.healthMetrics = HealthBodyMetrics(weightKg = 82.5f, restingHr = 52, vo2Max = 48.0)
        val p = s.calorieProfile()
        assertEquals(82.5f, p.weightKg)
        assertEquals(52, p.restingHr)
        assertEquals(48.0, p.vo2Max)
        s.healthMetrics = HealthBodyMetrics()
        assertEquals(70f, s.calorieProfile().weightKg)
    }

    @Test
    fun ageFromBirthDate() {
        val s = AppSettings().apply { userBirthDate = LocalDate(1987, 6, 1) }
        assertEquals(39, s.userAgeYears(LocalDate(2026, 11, 14)))
        assertEquals(38, s.userAgeYears(LocalDate(2026, 5, 31)))
        assertEquals(CalorieProfile.DEFAULT_AGE_YEARS, AppSettings().calorieProfile().ageYears)
    }
}
