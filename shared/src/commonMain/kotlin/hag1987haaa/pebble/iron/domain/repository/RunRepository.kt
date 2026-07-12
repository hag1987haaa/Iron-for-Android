package hag1987haaa.pebble.iron.domain.repository

import kotlinx.coroutines.flow.Flow
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity

interface RunRepository {
    suspend fun saveRun(run: RunActivity): Long
    fun getAllRuns(): Flow<List<RunActivity>>
    suspend fun getAllRunsWithDetails(): List<RunActivity>
    suspend fun importRuns(runs: List<RunActivity>)
    suspend fun getRunDetails(runId: Long): RunActivity?
    suspend fun deleteRun(runId: Long)
    suspend fun updateRunName(runId: Long, name: String)
    suspend fun updateActivityType(runId: Long, type: hag1987haaa.pebble.iron.domain.model.ActivityType, userWeight: Float? = null)
    suspend fun updateHealthConnectId(runId: Long, healthConnectId: String?)
}
