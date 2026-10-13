package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeLocationTracker : LocationTracker {
    override fun startTracking(): Flow<LocationPoint> = emptyFlow()
    override fun stopTracking() {}
}

class FakePebbleMessenger : PebbleMessenger {
    val sentNotifications = mutableListOf<Int>()

    override fun sendStatistics(stats: RunStatistics) {}
    override fun sendState(status: RunStatus, stats: RunStatistics) {}
    override fun sendFullSync(stats: RunStatistics) {}
    override fun sendGraphData(stats: RunStatistics) {}
    override fun rotateGraphType(stats: RunStatistics) {}
    override fun rotateMidData(stats: RunStatistics) {}
    override fun sendTouchConfig(enabled: Boolean) {}
    override fun sendNotification(type: Int) {
        sentNotifications.add(type)
    }
    override fun sendLowerData(lowerDataString: String) {}
    override fun sendMidId(id: Int) {}
    override fun sendLowerId(id: Int) {}
    override fun sendMapState(isActive: Boolean) {}
    override fun sendMapChunk(data: ByteArray, chunkIdx: Int, totalChunks: Int) {}
    override fun setCurrentMidId(id: Int) {}
    override fun setCurrentLowerId(id: Int) {}
    override fun setMapState(isActive: Boolean) {}
    override fun sendMap(points: List<LocationPoint>, width: Int, height: Int, zoom: Int?) {}
    override fun launchWatchApp() {}
    override fun requestWatchInfo() {}
}

class RunTrackerEngineHrZoneTest {

    private fun createEngine(
        settings: AppSettings,
        messenger: FakePebbleMessenger
    ): RunTrackerEngine {
        return RunTrackerEngine(
            locationTracker = FakeLocationTracker(),
            pebbleMessenger = messenger,
            appSettings = settings,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    @Test
    fun testInitialStateTriggersLowHrAlertAfterOutDuration() {
        val settings = AppSettings().apply {
            isHrZoneNotificationEnabled = true
            hrZoneOutDurationSeconds = 5
            hrZoneRepeatIntervalSeconds = 10
            setHrZoneMin(ActivityType.RUNNING, 130)
            setHrZoneMax(ActivityType.RUNNING, 160)
        }
        val messenger = FakePebbleMessenger()
        val engine = createEngine(settings, messenger)

        // 心拍数 100 bpm（低すぎる）を4秒間シミュレーション -> まだ outDuration (5s) 未満なので鳴らない
        repeat(4) {
            engine.checkHeartRateZoneNotification(100)
        }
        assertTrue(messenger.sentNotifications.isEmpty())

        // 5秒目で 22 (HR TOO LOW) が鳴る！
        engine.checkHeartRateZoneNotification(100)
        assertEquals(listOf(22), messenger.sentNotifications)

        // その後10秒後 (repeatInterval) で再度 22 (HR TOO LOW) が鳴る！
        repeat(10) {
            engine.checkHeartRateZoneNotification(100)
        }
        assertEquals(listOf(22, 22), messenger.sentNotifications)
    }

    @Test
    fun testEntersZoneAndTriggersInZoneNotification() {
        val settings = AppSettings().apply {
            isHrZoneNotificationEnabled = true
            hrZoneOutDurationSeconds = 5
            hrZoneRepeatIntervalSeconds = 10
            setHrZoneMin(ActivityType.RUNNING, 130)
            setHrZoneMax(ActivityType.RUNNING, 160)
        }
        val messenger = FakePebbleMessenger()
        val engine = createEngine(settings, messenger)

        // ゾーン内 (140 bpm) を2秒 -> まだ鳴らない
        repeat(2) { engine.checkHeartRateZoneNotification(140) }
        assertTrue(messenger.sentNotifications.isEmpty())

        // 3秒目 -> 20 (IN_ZONE) が1回鳴る
        engine.checkHeartRateZoneNotification(140)
        assertEquals(listOf(20), messenger.sentNotifications)

        // さらにゾーン内を維持 -> 再通知は鳴らない
        repeat(10) { engine.checkHeartRateZoneNotification(140) }
        assertEquals(listOf(20), messenger.sentNotifications)
    }

    @Test
    fun testHrTooLowTriggersWarningAndRepeatsAtInterval() {
        val settings = AppSettings().apply {
            isHrZoneNotificationEnabled = true
            hrZoneOutDurationSeconds = 5
            hrZoneRepeatIntervalSeconds = 10
            setHrZoneMin(ActivityType.RUNNING, 130)
            setHrZoneMax(ActivityType.RUNNING, 160)
        }
        val messenger = FakePebbleMessenger()
        val engine = createEngine(settings, messenger)

        // まずゾーンに入る (3秒)
        repeat(3) { engine.checkHeartRateZoneNotification(140) }
        assertEquals(listOf(20), messenger.sentNotifications)

        // ペースが落ちて心拍数が 120 bpm (< 130) に低下
        // 4秒間 -> まだ outDuration (5s) 未満なので鳴らない
        repeat(4) { engine.checkHeartRateZoneNotification(120) }
        assertEquals(listOf(20), messenger.sentNotifications)

        // 5秒目 -> 22 (HR TOO LOW) が鳴る！
        engine.checkHeartRateZoneNotification(120)
        assertEquals(listOf(20, 22), messenger.sentNotifications)

        // その後も 120 bpm のまま（1〜9秒後） -> まだ repeatInterval (10s) 未満なので鳴らない
        repeat(9) { engine.checkHeartRateZoneNotification(120) }
        assertEquals(listOf(20, 22), messenger.sentNotifications)

        // 10秒後 -> 再度 22 (HR TOO LOW) が鳴る！（再アラーム）
        engine.checkHeartRateZoneNotification(120)
        assertEquals(listOf(20, 22, 22), messenger.sentNotifications)

        // さらに 10秒後 -> 再度 22 (HR TOO LOW) が鳴る！（2回目の再アラーム）
        repeat(10) { engine.checkHeartRateZoneNotification(120) }
        assertEquals(listOf(20, 22, 22, 22), messenger.sentNotifications)

        // ユーザーがペースを上げてゾーン内 (140 bpm) に復帰 (3秒安定)
        repeat(3) { engine.checkHeartRateZoneNotification(140) }
        assertEquals(listOf(20, 22, 22, 22, 20), messenger.sentNotifications)

        // ゾーン内維持中は再通知されない
        repeat(20) { engine.checkHeartRateZoneNotification(140) }
        assertEquals(listOf(20, 22, 22, 22, 20), messenger.sentNotifications)
    }

    @Test
    fun testHrTooHighTriggersWarningAndRepeatsAtInterval() {
        val settings = AppSettings().apply {
            isHrZoneNotificationEnabled = true
            hrZoneOutDurationSeconds = 5
            hrZoneRepeatIntervalSeconds = 15
            setHrZoneMin(ActivityType.RUNNING, 130)
            setHrZoneMax(ActivityType.RUNNING, 160)
        }
        val messenger = FakePebbleMessenger()
        val engine = createEngine(settings, messenger)

        // まずゾーンに入る (3秒)
        repeat(3) { engine.checkHeartRateZoneNotification(145) }
        assertEquals(listOf(20), messenger.sentNotifications)

        // 心拍数が 170 bpm (> 160) に急上昇
        // 5秒目 -> 21 (HR TOO HIGH) が鳴る！
        repeat(5) { engine.checkHeartRateZoneNotification(170) }
        assertEquals(listOf(20, 21), messenger.sentNotifications)

        // その後も 170 bpm のまま（1〜14秒後） -> まだ鳴らない
        repeat(14) { engine.checkHeartRateZoneNotification(170) }
        assertEquals(listOf(20, 21), messenger.sentNotifications)

        // 15秒後 -> 再度 21 (HR TOO HIGH) が鳴る！（再アラーム）
        engine.checkHeartRateZoneNotification(170)
        assertEquals(listOf(20, 21, 21), messenger.sentNotifications)
    }

    @Test
    fun testRepeatIntervalDisabledDoesNotRepeat() {
        val settings = AppSettings().apply {
            isHrZoneNotificationEnabled = true
            hrZoneOutDurationSeconds = 5
            hrZoneRepeatIntervalSeconds = 0 // オフ
            setHrZoneMin(ActivityType.RUNNING, 130)
            setHrZoneMax(ActivityType.RUNNING, 160)
        }
        val messenger = FakePebbleMessenger()
        val engine = createEngine(settings, messenger)

        // ゾーンに入る
        repeat(3) { engine.checkHeartRateZoneNotification(140) }
        assertEquals(listOf(20), messenger.sentNotifications)

        // 心拍数低下 -> 5秒後に 22 (HR TOO LOW) が鳴る
        repeat(5) { engine.checkHeartRateZoneNotification(110) }
        assertEquals(listOf(20, 22), messenger.sentNotifications)

        // リピート間隔が 0（オフ）の場合、その後何秒経っても再アラームは鳴らない
        repeat(60) { engine.checkHeartRateZoneNotification(110) }
        assertEquals(listOf(20, 22), messenger.sentNotifications)
    }
}
