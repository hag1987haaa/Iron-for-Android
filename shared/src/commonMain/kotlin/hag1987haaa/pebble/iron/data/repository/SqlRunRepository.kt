package hag1987haaa.pebble.iron.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import hag1987haaa.pebble.iron.db.PebbleTrackerDatabase
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import hag1987haaa.pebble.iron.util.HealthUtils

class SqlRunRepository(db: PebbleTrackerDatabase) : RunRepository {

    private val queries = db.runActivityQueries

    override suspend fun saveRun(run: RunActivity) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.insertRun(
                run.name,
                run.type.name,
                run.startTime.toString(),
            )
            val runId = queries.lastInsertId().executeAsOne()

            run.route.forEach { point ->
                queries.insertLocation(
                    runId = runId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitude = point.altitude,
                    speed = point.speed,
                    heartRate = point.heartRate?.toLong(),
                    steps = point.steps?.toLong(),
                    timestamp = point.timestamp.toString(),
                )
            }

            queries.updateRun(
                name = run.name,
                endTime = run.endTime?.toString(),
                distanceMeters = run.distanceMeters,
                durationSeconds = run.durationSeconds,
                averagePaceSecondsPerKm = run.averagePaceSecondsPerKm,
                calories = run.calories,
                steps = run.steps?.toLong(),
                avgHeartRate = run.avgHeartRate,
                maxHeartRate = run.maxHeartRate,
                elevationGain = run.elevationGain,
                healthConnectId = run.healthConnectId,
                id = runId,
            )
        }
    }

    override fun getAllRuns(): Flow<List<RunActivity>> {
        return queries.getAllRuns()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        RunActivity(
                            id = entity.id,
                            name = entity.name,
                            type = try {
                                ActivityType.valueOf(entity.type)
                            } catch (_: Exception) {
                                ActivityType.OTHER
                            },
                            startTime = try {
                                Instant.parse(entity.startTime)
                            } catch (_: Exception) {
                                Instant.fromEpochMilliseconds(0)
                            },
                            endTime = entity.endTime?.let {
                                try {
                                    Instant.parse(it)
                                } catch (_: Exception) {
                                    null
                                }
                            },
                            distanceMeters = entity.distanceMeters,
                            durationSeconds = entity.durationSeconds,
                            averagePaceSecondsPerKm = entity.averagePaceSecondsPerKm,
                            calories = entity.calories,
                            steps = entity.steps?.toInt(),
                            avgHeartRate = entity.avgHeartRate,
                            maxHeartRate = entity.maxHeartRate,
                            elevationGain = entity.elevationGain,
                            healthConnectId = entity.healthConnectId,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
    }

    override suspend fun getAllRunsWithDetails(): List<RunActivity> = withContext(Dispatchers.IO) {
        val runs = queries.getAllRuns().executeAsList()
        runs.map { runEntity ->
            val locations = queries.getLocationsForRun(runEntity.id).executeAsList().map {
                LocationPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    speed = it.speed,
                    heartRate = it.heartRate?.toInt(),
                    steps = it.steps?.toInt(),
                    timestamp = Instant.parse(it.timestamp),
                )
            }

            RunActivity(
                id = runEntity.id,
                name = runEntity.name,
                type = try {
                    ActivityType.valueOf(runEntity.type)
                } catch (_: Exception) {
                    ActivityType.OTHER
                },
                startTime = Instant.parse(runEntity.startTime),
                endTime = runEntity.endTime?.let { Instant.parse(it) },
                distanceMeters = runEntity.distanceMeters,
                durationSeconds = runEntity.durationSeconds,
                averagePaceSecondsPerKm = runEntity.averagePaceSecondsPerKm,
                calories = runEntity.calories,
                steps = runEntity.steps?.toInt(),
                avgHeartRate = runEntity.avgHeartRate,
                maxHeartRate = runEntity.maxHeartRate,
                elevationGain = runEntity.elevationGain,
                healthConnectId = runEntity.healthConnectId,
                route = locations,
            )
        }
    }

    override suspend fun importRuns(runs: List<RunActivity>) = withContext(Dispatchers.IO) {
        queries.transaction {
            runs.forEach { run ->
                queries.insertRun(run.name, run.type.name, run.startTime.toString())
                val runId = queries.lastInsertId().executeAsOne()

                run.route.forEach { point ->
                    queries.insertLocation(
                        runId = runId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitude,
                        speed = point.speed,
                        heartRate = point.heartRate?.toLong(),
                        steps = point.steps?.toLong(),
                        timestamp = point.timestamp.toString(),
                    )
                }

                queries.updateRun(
                    name = run.name,
                    endTime = run.endTime?.toString(),
                    distanceMeters = run.distanceMeters,
                    durationSeconds = run.durationSeconds,
                    averagePaceSecondsPerKm = run.averagePaceSecondsPerKm,
                    calories = run.calories,
                    steps = run.steps?.toLong(),
                    avgHeartRate = run.avgHeartRate,
                    maxHeartRate = run.maxHeartRate,
                    elevationGain = run.elevationGain,
                    healthConnectId = run.healthConnectId,
                    id = runId,
                )
            }
        }
    }

    override suspend fun getRunDetails(runId: Long): RunActivity? = withContext(Dispatchers.IO) {
        val runEntity = queries.getAllRuns().executeAsList().find { it.id == runId } ?: return@withContext null
        val locations = queries.getLocationsForRun(runId).executeAsList().map {
            LocationPoint(
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
                speed = it.speed,
                heartRate = it.heartRate?.toInt(),
                steps = it.steps?.toInt(),
                timestamp = Instant.parse(it.timestamp),
            )
        }

        RunActivity(
            id = runEntity.id,
            name = runEntity.name,
            type = try {
                ActivityType.valueOf(runEntity.type)
            } catch (_: Exception) {
                ActivityType.OTHER
            },
            startTime = Instant.parse(runEntity.startTime),
            endTime = runEntity.endTime?.let { Instant.parse(it) },
            distanceMeters = runEntity.distanceMeters,
            durationSeconds = runEntity.durationSeconds,
            averagePaceSecondsPerKm = runEntity.averagePaceSecondsPerKm,
            calories = runEntity.calories,
            steps = runEntity.steps?.toInt(),
            avgHeartRate = runEntity.avgHeartRate,
            maxHeartRate = runEntity.maxHeartRate,
            elevationGain = runEntity.elevationGain,
            healthConnectId = runEntity.healthConnectId,
            route = locations,
        )
    }

    override suspend fun deleteRun(runId: Long) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.deleteLocationsForRun(runId)
            queries.deleteRun(runId)
        }
    }

    override suspend fun updateRunName(runId: Long, name: String) = withContext(Dispatchers.IO) {
        queries.updateRunName(name, runId)
    }

    override suspend fun updateActivityType(runId: Long, type: ActivityType, userWeight: Float?) = withContext(Dispatchers.IO) {
        if (userWeight == null) {
            queries.updateActivityType(type.name, runId)
        } else {
            val run = getRunDetails(runId)
            if (run != null) {
                val newCalories = HealthUtils.calculateCalories(
                    type = type,
                    weightKg = userWeight,
                    durationSeconds = run.durationSeconds,
                    distanceMeters = run.distanceMeters,
                    elevationGainMeters = run.elevationGain ?: 0.0,
                    avgHeartRate = run.avgHeartRate,
                )
                queries.updateActivityTypeAndCalories(type.name, newCalories, runId)
            } else {
                queries.updateActivityType(type.name, runId)
            }
        }
    }

    override suspend fun updateHealthConnectId(runId: Long, healthConnectId: String?) = withContext(Dispatchers.IO) {
        queries.updateHealthConnectId(healthConnectId, runId)
    }
}
