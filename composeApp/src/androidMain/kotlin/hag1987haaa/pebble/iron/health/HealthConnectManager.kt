package hag1987haaa.pebble.iron.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata as HealthMetadata
import androidx.health.connect.client.units.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.toJavaInstant
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    
    fun isSdkAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    // 権限セットを定義（書き込み権限のみに制限）
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        // 運動ルート権限 (Android 14+ で詳細なマップ表示に必須)
        "android.permission.health.WRITE_EXERCISE_ROUTE",
        "android.permission.health.WRITE_ELEVATION_GAINED",
    )

    suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun writeRunActivity(run: RunActivity): String? {
        try {
            if (!hasAllPermissions()) return null

            val startTime = run.startTime.toJavaInstant()
            val endTime = (run.endTime ?: run.startTime).toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

            // 1. 運動ルートの作成
            val exerciseRoute = if (run.route.isNotEmpty()) {
                ExerciseRoute(
                    route = run.route.map {
                        ExerciseRoute.Location(
                            time = it.timestamp.toJavaInstant(),
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = it.altitude?.let { a -> Length.meters(a) }
                        )
                    }
                )
            } else null

            // 2. エクササイズセッションの作成
            val exerciseType = when (run.type) {
                ActivityType.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                ActivityType.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                ActivityType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                ActivityType.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
                ActivityType.KAYAKING -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING
                ActivityType.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
                ActivityType.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            }

            val startTimeMillis = run.startTime.toEpochMilliseconds()
            val metadata = HealthMetadata(clientRecordId = "iron_session_$startTimeMillis")

            @Suppress("RestrictedApi")
            val sessionRecord = ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                exerciseType = exerciseType,
                title = run.name ?: "Workout",
                notes = "Recorded via Iron for pebble",
                metadata = metadata,
                exerciseRoute = exerciseRoute,
            )

            // 2.5 獲得標高データの作成
            val elevationGainedRecord = run.elevationGain?.let {
                if (it <= 0) return@let null
                ElevationGainedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    elevation = Length.meters(it),
                    metadata = HealthMetadata(clientRecordId = "iron_elev_$startTimeMillis")
                )
            }

            // 3. 距離データの作成
            val distanceRecord = DistanceRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                distance = Length.meters(run.distanceMeters),
                metadata = HealthMetadata(clientRecordId = "iron_dist_$startTimeMillis")
            )

            // 4. 心拍数データの作成
            val samples = run.route.asSequence().mapNotNull { point ->
                val bpm = point.heartRate?.toLong()
                if (bpm != null && bpm > 0) {
                    HeartRateRecord.Sample(
                        time = point.timestamp.toJavaInstant(),
                        beatsPerMinute = bpm
                    )
                } else null
            }.toList()
            val heartRateRecord = if (samples.isNotEmpty()) {
                HeartRateRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    samples = samples,
                    metadata = HealthMetadata(clientRecordId = "iron_hr_$startTimeMillis")
                )
            } else null

            // 5. カロリーデータの作成 (運動による消費カロリーとして記録)
            val caloriesRecord = run.calories?.let {
                ActiveCaloriesBurnedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    energy = Energy.kilocalories(it),
                    metadata = HealthMetadata(clientRecordId = "iron_cal_$startTimeMillis")
                )
            }

            // 6. 歩数データの作成
            val stepsRecord = run.steps?.let {
                if (it <= 0) return@let null
                StepsRecord(
                    count = it.toLong(),
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    metadata = HealthMetadata(clientRecordId = "iron_steps_$startTimeMillis")
                )
            }

            val records = mutableListOf<Record>(sessionRecord, distanceRecord)
            heartRateRecord?.let { records.add(it) }
            caloriesRecord?.let { records.add(it) }
            stepsRecord?.let { records.add(it) }
            elevationGainedRecord?.let { records.add(it) }
            
            Log.d("HealthConnect", "Inserting ${records.size} records for session: ${run.name ?: "Workout"}")
            val response = healthConnectClient.insertRecords(records)
            val firstId = response.recordIdsList.firstOrNull()
            Log.d("HealthConnect", "Insert successful. First Record ID: $firstId")
            return firstId
        } catch (e: Exception) {
            Log.e("HealthConnect", "Write failed with details: ${e.message}", e)
            return null
        }
    }

    suspend fun deleteRunActivity(run: RunActivity) {
        try {
            val startTimeMillis = run.startTime.toEpochMilliseconds()
            val clientRecordIds = listOf(
                "iron_session_$startTimeMillis",
                "iron_dist_$startTimeMillis",
                "iron_hr_$startTimeMillis",
                "iron_cal_$startTimeMillis",
                "iron_steps_$startTimeMillis",
                "iron_elev_$startTimeMillis"
            )

            val recordTypes = listOf(
                ExerciseSessionRecord::class,
                DistanceRecord::class,
                HeartRateRecord::class,
                ActiveCaloriesBurnedRecord::class,
                StepsRecord::class,
                ElevationGainedRecord::class
            )
            
            recordTypes.forEach { recordType ->
                try {
                    healthConnectClient.deleteRecords(
                        recordType,
                        recordIdsList = emptyList(),
                        clientRecordIdsList = clientRecordIds
                    )
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Delete failed", e)
        }
    }
}
