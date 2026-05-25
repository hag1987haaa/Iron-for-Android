package hag1987haaa.pebble.iron

import hag1987haaa.pebble.iron.domain.tracker.RunTrackerEngine
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import hag1987haaa.pebble.iron.domain.settings.AppSettings

object KmpDependencies {
    private var _trackerEngine: RunTrackerEngine? = null
    private var _runRepository: RunRepository? = null
    private var _appSettings: AppSettings? = null
    
    val runRepository: RunRepository
        get() = _runRepository ?: throw IllegalStateException("Not initialized")

    val trackerEngine: RunTrackerEngine
        get() = _trackerEngine ?: throw IllegalStateException("Not initialized")

    val appSettings: AppSettings
        get() = _appSettings ?: throw IllegalStateException("Not initialized")

    fun setup(
        repository: RunRepository,
        engine: RunTrackerEngine,
        settings: AppSettings
    ) {
        _runRepository = repository
        _trackerEngine = engine
        _appSettings = settings
    }
}
