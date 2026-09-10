package hag1987haaa.pebble.iron.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata as HealthMetadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.toJavaInstant
import java.time.ZoneOffset

sealed class HealthSyncResult {
    data class Success(val recordId: String) : HealthSyncResult()
    data class PermissionDenied(val missingPermissions: List<String>) : HealthSyncResult()
    data class Error(val message: String, val throwable: Throwable? = null) : HealthSyncResult()
}

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    // 個別権限の定義
    val sessionPermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val distancePermission = HealthPermission.getWritePermission(DistanceRecord::class)
    val heartRatePermission = HealthPermission.getWritePermission(HeartRateRecord::class)
    val caloriesPermission = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    val stepsPermission = HealthPermission.getWritePermission(StepsRecord::class)
    val elevationPermission = HealthPermission.getWritePermission(ElevationGainedRecord::class)
    val routePermission = "android.permission.health.WRITE_EXERCISE_ROUTE"

    // 基本書き込み権限セット
    val basePermissions = setOf(
        sessionPermission,
        distancePermission,
        heartRatePermission,
        caloriesPermission,
        stepsPermission,
        elevationPermission
    )

    // 全権限（運動ルート権限を含む）
    val permissions = basePermissions + setOf(routePermission)

    suspend fun hasSessionPermission(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(sessionPermission)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to check session permission", e)
            false
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(basePermissions)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to check permissions", e)
            false
        }
    }

    suspend fun writeRunActivityResult(run: RunActivity): HealthSyncResult {
        try {
            val granted = try {
                healthConnectClient.permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                Log.e("HealthConnect", "Failed to get granted permissions", e)
                return HealthSyncResult.Error("Failed to check permissions: ${e.message}", e)
            }

            // 必須のセッション権限チェック (セッションすら許可されていない場合は即座に中断)
            if (!granted.contains(sessionPermission)) {
                Log.w("HealthConnect", "Write aborted: Exercise Session permission is not granted.")
                return HealthSyncResult.PermissionDenied(listOf(sessionPermission))
            }

            val startTime = run.startTime.toJavaInstant()
            val endTime = (run.endTime ?: run.startTime).toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

            // 1. 運動ルートの作成 (ルート権限が付与されている場合のみ付与)
            val canWriteRoute = granted.contains(routePermission)
            val exerciseRoute = if (canWriteRoute && run.route.isNotEmpty()) {
                try {
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
                } catch (e: Exception) {
                    Log.w("HealthConnect", "Failed to construct ExerciseRoute: ${e.message}")
                    null
                }
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

            val records = mutableListOf<Record>(sessionRecord)

            // 2.5 獲得標高データの作成 (権限がある場合のみ)
            if (granted.contains(elevationPermission)) {
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
                elevationGainedRecord?.let { records.add(it) }
            }

            // 3. 距離データの作成 (権限がある場合のみ)
            if (granted.contains(distancePermission)) {
                val distanceRecord = DistanceRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    distance = Length.meters(run.distanceMeters),
                    metadata = HealthMetadata(clientRecordId = "iron_dist_$startTimeMillis")
                )
                records.add(distanceRecord)
            }

            // 4. 心拍数データの作成 (権限がある場合のみ)
            if (granted.contains(heartRatePermission)) {
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
                heartRateRecord?.let { records.add(it) }
            }

            // 5. カロリーデータの作成 (権限がある場合のみ)
            if (granted.contains(caloriesPermission)) {
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
                caloriesRecord?.let { records.add(it) }
            }

            // 6. 歩数データの作成 (権限がある場合のみ)
            if (granted.contains(stepsPermission)) {
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
                stepsRecord?.let { records.add(it) }
            }

            Log.d("HealthConnect", "Inserting ${records.size} records for session: ${run.name ?: "Workout"}")
            val response = healthConnectClient.insertRecords(records)
            val firstId = response.recordIdsList.firstOrNull()
            Log.d("HealthConnect", "Insert successful. First Record ID: $firstId")
            return if (firstId != null) {
                HealthSyncResult.Success(firstId)
            } else {
                HealthSyncResult.Error("Health Connect returned empty record ID")
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Write failed with details: ${e.message}", e)
            return HealthSyncResult.Error(e.message ?: "Health Connect write failed", e)
        }
    }

    suspend fun writeRunActivity(run: RunActivity): String? {
        return when (val result = writeRunActivityResult(run)) {
            is HealthSyncResult.Success -> result.recordId
            else -> null
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
