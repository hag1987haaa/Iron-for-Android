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
        if (!isAvailable()) return false
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(sessionPermission)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to check session permission", e)
            false
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(basePermissions)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Failed to check permissions", e)
            false
        }
    }

    suspend fun writeRunActivityResult(run: RunActivity): HealthSyncResult {
        if (!isAvailable()) {
            return HealthSyncResult.Error("Health Connect is not available on this device")
        }
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

            // clientRecordId には DB 上の元の startTime を使用して、既存データの更新・削除との一貫性を維持
            val startTimeMillis = run.startTime.toEpochMilliseconds()
            val sanitizedRun = run.withSanitizedTimestamps()

            val startTime = sanitizedRun.startTime.toJavaInstant()
            val endTime = (sanitizedRun.endTime ?: sanitizedRun.startTime).toJavaInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

            // 1. 運動ルートの作成 (ルート権限が付与されている場合のみ付与)
            val canWriteRoute = granted.contains(routePermission)
            val validRoutePoints = if (canWriteRoute && sanitizedRun.route.isNotEmpty()) {
                val filtered = sanitizedRun.route.filter { pt ->
                    pt.latitude.isFinite() && pt.latitude in -90.0..90.0 &&
                        pt.longitude.isFinite() && pt.longitude in -180.0..180.0
                }
                // 長時間ワークアウト等でポイント数が極端に多い場合、Binder IPC 制限 (TransactionTooLargeException) を防ぐため最大 7,500 点に安全に間引く
                val maxRoutePoints = 7500
                if (filtered.size > maxRoutePoints) {
                    val step = (filtered.size - 1).toDouble() / (maxRoutePoints - 1)
                    (0 until maxRoutePoints).map { idx ->
                        filtered[(idx * step).toInt().coerceIn(0, filtered.lastIndex)]
                    }.distinctBy { it.timestamp }
                } else {
                    filtered
                }
            } else emptyList()

            val exerciseRoute = if (validRoutePoints.isNotEmpty()) {
                try {
                    ExerciseRoute(
                        route = validRoutePoints.map { pt ->
                            val safeAlt = pt.altitude?.takeIf { it.isFinite() && it in -1000.0..20000.0 }
                            ExerciseRoute.Location(
                                time = pt.timestamp.toJavaInstant(),
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                altitude = safeAlt?.let { a -> Length.meters(a) }
                            )
                        }
                    )
                } catch (e: Exception) {
                    Log.w("HealthConnect", "Failed to construct ExerciseRoute: ${e.message}")
                    null
                }
            } else null

            // 2. エクササイズセッションの作成
            val exerciseType = when (sanitizedRun.type) {
                ActivityType.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                ActivityType.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                ActivityType.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                ActivityType.HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
                ActivityType.KAYAKING -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING
                ActivityType.ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
                ActivityType.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            }

            val metadata = HealthMetadata(clientRecordId = "iron_session_$startTimeMillis")

            @Suppress("RestrictedApi")
            fun buildSessionRecord(route: ExerciseRoute?): ExerciseSessionRecord {
                return ExerciseSessionRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    exerciseType = exerciseType,
                    title = (run.name ?: "Workout").ifBlank { "Workout" },
                    notes = "Recorded via Iron for pebble",
                    metadata = metadata,
                    exerciseRoute = route,
                )
            }

            val sessionRecord = try {
                buildSessionRecord(exerciseRoute)
            } catch (e: Exception) {
                Log.w("HealthConnect", "SessionRecord with route failed (${e.message}), falling back to route=null")
                buildSessionRecord(null)
            }

            val records = mutableListOf<Record>(sessionRecord)

            // 2.5 獲得標高データの作成 (権限がある場合、かつ正の有効値のみ)
            if (granted.contains(elevationPermission)) {
                val elevationGainedRecord = sanitizedRun.elevationGain?.let {
                    if (!it.isFinite() || it <= 0.0) return@let null
                    try {
                        ElevationGainedRecord(
                            startTime = startTime,
                            startZoneOffset = zoneOffset,
                            endTime = endTime,
                            endZoneOffset = zoneOffset,
                            elevation = Length.meters(it),
                            metadata = HealthMetadata(clientRecordId = "iron_elev_$startTimeMillis")
                        )
                    } catch (e: Exception) {
                        Log.w("HealthConnect", "Skipping invalid ElevationGainedRecord: ${e.message}")
                        null
                    }
                }
                elevationGainedRecord?.let { records.add(it) }
            }

            // 3. 距離データの作成 (権限がある場合、かつ正の有効値のみ)
            if (granted.contains(distancePermission)) {
                val distMeters = sanitizedRun.distanceMeters
                if (distMeters.isFinite() && distMeters > 0.0) {
                    try {
                        val distanceRecord = DistanceRecord(
                            startTime = startTime,
                            startZoneOffset = zoneOffset,
                            endTime = endTime,
                            endZoneOffset = zoneOffset,
                            distance = Length.meters(distMeters),
                            metadata = HealthMetadata(clientRecordId = "iron_dist_$startTimeMillis")
                        )
                        records.add(distanceRecord)
                    } catch (e: Exception) {
                        Log.w("HealthConnect", "Skipping invalid DistanceRecord: ${e.message}")
                    }
                }
            }

            // 4. 心拍数データの作成 (権限がある場合、1..300 bpm かつ同一時刻の重複を排除)
            if (granted.contains(heartRatePermission)) {
                val rawSamples = sanitizedRun.route.asSequence().mapNotNull { point ->
                    val bpm = point.heartRate?.toLong()
                    if (bpm != null && bpm in 1L..300L) {
                        HeartRateRecord.Sample(
                            time = point.timestamp.toJavaInstant(),
                            beatsPerMinute = bpm
                        )
                    } else null
                }.distinctBy { it.time }.toList()

                val maxHrSamples = 5000
                val samples = if (rawSamples.size > maxHrSamples) {
                    val step = (rawSamples.size - 1).toDouble() / (maxHrSamples - 1)
                    (0 until maxHrSamples).map { idx ->
                        rawSamples[(idx * step).toInt().coerceIn(0, rawSamples.lastIndex)]
                    }.distinctBy { it.time }
                } else {
                    rawSamples
                }

                val heartRateRecord = if (samples.isNotEmpty()) {
                    try {
                        HeartRateRecord(
                            startTime = startTime,
                            startZoneOffset = zoneOffset,
                            endTime = endTime,
                            endZoneOffset = zoneOffset,
                            samples = samples,
                            metadata = HealthMetadata(clientRecordId = "iron_hr_$startTimeMillis")
                        )
                    } catch (e: Exception) {
                        Log.w("HealthConnect", "Skipping invalid HeartRateRecord: ${e.message}")
                        null
                    }
                } else null
                heartRateRecord?.let { records.add(it) }
            }

            // 5. カロリーデータの作成 (権限がある場合、かつ正の有効値のみ)
            if (granted.contains(caloriesPermission)) {
                val caloriesRecord = sanitizedRun.calories?.let {
                    if (!it.isFinite() || it <= 0.0) return@let null
                    try {
                        ActiveCaloriesBurnedRecord(
                            startTime = startTime,
                            startZoneOffset = zoneOffset,
                            endTime = endTime,
                            endZoneOffset = zoneOffset,
                            energy = Energy.kilocalories(it),
                            metadata = HealthMetadata(clientRecordId = "iron_cal_$startTimeMillis")
                        )
                    } catch (e: Exception) {
                        Log.w("HealthConnect", "Skipping invalid ActiveCaloriesBurnedRecord: ${e.message}")
                        null
                    }
                }
                caloriesRecord?.let { records.add(it) }
            }

            // 6. 歩数データの作成 (権限がある場合、かつ正の有効値のみ)
            if (granted.contains(stepsPermission)) {
                val stepsRecord = sanitizedRun.steps?.let {
                    if (it <= 0) return@let null
                    try {
                        StepsRecord(
                            count = it.toLong(),
                            startTime = startTime,
                            startZoneOffset = zoneOffset,
                            endTime = endTime,
                            endZoneOffset = zoneOffset,
                            metadata = HealthMetadata(clientRecordId = "iron_steps_$startTimeMillis")
                        )
                    } catch (e: Exception) {
                        Log.w("HealthConnect", "Skipping invalid StepsRecord: ${e.message}")
                        null
                    }
                }
                stepsRecord?.let { records.add(it) }
            }

            Log.d("HealthConnect", "Inserting ${records.size} records for session: ${run.name ?: "Workout"}")
            val response = try {
                healthConnectClient.insertRecords(records)
            } catch (e: Exception) {
                // 万が一 ExerciseRoute 起因で insertRecords 自体が拒否された場合、ルートを除いて再試行しセッションと統計データを守る
                if (exerciseRoute != null) {
                    Log.w("HealthConnect", "insertRecords with route failed (${e.message}), retrying without ExerciseRoute")
                    records[0] = buildSessionRecord(null)
                    healthConnectClient.insertRecords(records)
                } else {
                    throw e
                }
            }
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
        if (!isAvailable()) return
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
