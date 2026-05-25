package hag1987haaa.pebble.iron.domain.location

import kotlinx.coroutines.flow.Flow
import hag1987haaa.pebble.iron.domain.model.LocationPoint

interface LocationTracker {
    fun startTracking(): Flow<LocationPoint>
    fun stopTracking()
}
