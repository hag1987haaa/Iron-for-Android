package hag1987haaa.pebble.iron

import hag1987haaa.pebble.iron.domain.tracker.RunTrackerEngine
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.ble.BleScanner
import hag1987haaa.pebble.iron.domain.ble.BleHeartRateManager
import hag1987haaa.pebble.iron.domain.ble.BleCscManager
import hag1987haaa.pebble.iron.domain.ble.BlePowerManager

object KmpDependencies {
    private var _trackerEngine: RunTrackerEngine? = null
    private var _runRepository: RunRepository? = null
    private var _appSettings: AppSettings? = null
    private var _bleScanner: BleScanner? = null
    private var _bleHeartRateManager: BleHeartRateManager? = null
    private var _bleCscManager: BleCscManager? = null
    private var _blePowerManager: BlePowerManager? = null
    
    val runRepository: RunRepository
        get() = _runRepository ?: throw IllegalStateException("Not initialized")

    val trackerEngine: RunTrackerEngine
        get() = _trackerEngine ?: throw IllegalStateException("Not initialized")

    val appSettings: AppSettings
        get() = _appSettings ?: throw IllegalStateException("Not initialized")

    val bleScanner: BleScanner
        get() = _bleScanner ?: throw IllegalStateException("Not initialized")

    val bleHeartRateManager: BleHeartRateManager
        get() = _bleHeartRateManager ?: throw IllegalStateException("Not initialized")

    val bleCscManager: BleCscManager?
        get() = _bleCscManager

    val blePowerManager: BlePowerManager?
        get() = _blePowerManager

    fun setup(
        repository: RunRepository,
        engine: RunTrackerEngine,
        settings: AppSettings,
        bleScanner: BleScanner? = null,
        bleHeartRateManager: BleHeartRateManager? = null,
        bleCscManager: BleCscManager? = null,
        blePowerManager: BlePowerManager? = null
    ) {
        _runRepository = repository
        _trackerEngine = engine
        _appSettings = settings
        _bleScanner = bleScanner
        _bleHeartRateManager = bleHeartRateManager
        _bleCscManager = bleCscManager
        _blePowerManager = blePowerManager
    }
}
