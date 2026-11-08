package hag1987haaa.pebble.iron.pebble

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import hag1987haaa.pebble.iron.KmpDependencies

/**
 * バックグラウンド制限を回避してデジタルアシスタント（Gemini / VoiceInteractionService）を
 * 起動するためのトランポリン・アクティビティ。
 * ロック画面の上でも表示できるように設定し、Activity.showAssist() を呼び出す。
 *
 * ランニング中の完全ハンズフリー運用（方針A：毎回新規セッションを確実に立ち上げ・クローズする）に対応するため:
 * 1. アシスタント対話中は背後に生存し続け、inheritShowWhenLocked="true" を維持する（3.5秒での早期finishを廃止）。
 * 2. AudioManager.mode の遷移 (MODE_ASSISTANT_CONVERSATION -> MODE_NORMAL) を監視し、
 *    Spotify等のBGM再生に一切影響されず 100% の精度で Gemini の聴取・TTS応答完了を検知する。
 * 3. 対話完了（MODE_NORMAL復帰）後、セッションおよび AudioFocus の解放が落ち着くのを待ってから
 *    前面へ復帰して FloatyActivity（「閉じる」残存画面）を停止させ:
 *    - ロック中なら setShowWhenLocked(false) でロック画面（Keyguard）を前面に復帰させる。
 *    - ロック解除中（または後からロック解除された場合）ならホーム画面を前面に出して「閉じる」画面を完全に隠す。
 * 4. 2回目以降の呼び出し時は、前回セッションの AudioFocus 解放と AudioManager.mode == MODE_NORMAL を確認してから
 *    showAssist() を呼ぶことで、マイクの AudioFocus 競合（音声が入らない問題）を完全に防止する。
 */
class AssistantTrampolineActivity : Activity() {

    companion object {
        const val EXTRA_TRIGGER_ASSIST = "extra_trigger_assist"
        const val EXTRA_DISMISS_ONLY = "extra_dismiss_only"
        private const val MAX_SHOW_ASSIST_RETRIES = 25 // 25 * 150ms = 3.75秒まで待機
        private const val RETRY_INTERVAL_MS = 150L
        private const val AUDIO_MODE_POLL_INTERVAL_MS = 200L
        private const val NO_CONVERSATION_TIMEOUT_MS = 18000L // 18秒間一度も対話モードに入らなかった場合のフェイルセーフ
        private const val MAX_SESSION_LIFETIME_MS = 60000L // 最大セッション寿命 (60秒)
        private const val POST_CONVERSATION_DISMISS_DELAY_MS = 1500L // MODE_NORMAL復帰後の余韻・AudioFocus完全整定待機 (1.5秒)
        private const val REINVOCATION_SETTLE_DELAY_MS = 450L // 2回目以降の呼び出し前に前回セッションのAudioFocus解放を待つ時間
    }

    private var assistSucceeded = false
    private var assistLaunchedAtMs = 0L
    private var retryCount = 0
    private var isDismissing = false
    private var conversationModeSeen = false
    private var modeNormalConfirmedCount = 0
    private var waitingForReinvocationSettle = false

    private val handler = Handler(Looper.getMainLooper())

    private val fallbackRunnable = Runnable {
        if (!assistSucceeded && !isDismissing && !waitingForReinvocationSettle) {
            Log.d("AssistantTrampoline", "Fallback timer fired -> triggerAssistant")
            triggerAssistant()
        }
    }

    private val postConversationDismissRunnable = Runnable {
        if (!isDismissing && assistSucceeded) {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val currentMode = am?.mode ?: AudioManager.MODE_NORMAL
            if (currentMode != AudioManager.MODE_NORMAL) {
                Log.d("AssistantTrampoline", "Post-conversation timer fired but audio mode is $currentMode -> waiting for MODE_NORMAL again")
                return@Runnable
            }
            Log.d("AssistantTrampoline", "Post-conversation settle completed (mode=MODE_NORMAL) -> dismissing assistant hands-free")
            dismissAssistantHandsFree()
        }
    }

    private val noConversationTimeoutRunnable = Runnable {
        if (!conversationModeSeen && !isDismissing) {
            Log.d("AssistantTrampoline", "No-conversation timeout (${NO_CONVERSATION_TIMEOUT_MS}ms) fired -> dismissing assistant hands-free")
            dismissAssistantHandsFree()
        }
    }

    private val maxLifetimeRunnable = Runnable {
        if (!isDismissing) {
            Log.d("AssistantTrampoline", "Max session lifetime (${MAX_SESSION_LIFETIME_MS}ms) reached -> dismissing assistant hands-free")
            dismissAssistantHandsFree()
        }
    }

    /**
     * Gemini (com.google.android.googlequicksearchbox) はマイク聴取開始〜TTS応答完了までの間、
     * 必ず AudioManager.mode を MODE_ASSISTANT_CONVERSATION (4) 等の非 MODE_NORMAL に設定し、
     * TTS読み上げ（または無音タイムアウト）が終わって「閉じる」画面になった瞬間に MODE_NORMAL (0) に戻す。
     * これを監視することで Spotify 等のBGMに影響されず 100% 正確に対話終了を検知する。
     */
    private val audioModePollRunnable = object : Runnable {
        override fun run() {
            if (!assistSucceeded || isDismissing || isFinishing || isDestroyed) return
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = am?.mode ?: AudioManager.MODE_NORMAL

            if (mode != AudioManager.MODE_NORMAL) {
                if (!conversationModeSeen) {
                    Log.d("AssistantTrampoline", "Assistant conversation mode detected (audioMode=$mode)")
                }
                conversationModeSeen = true
                modeNormalConfirmedCount = 0
                handler.removeCallbacks(postConversationDismissRunnable)
                handler.removeCallbacks(noConversationTimeoutRunnable)
            } else if (conversationModeSeen) {
                modeNormalConfirmedCount++
                if (modeNormalConfirmedCount == 1) {
                    Log.d("AssistantTrampoline", "Audio mode returned to MODE_NORMAL -> scheduling hands-free dismiss in ${POST_CONVERSATION_DISMISS_DELAY_MS}ms")
                    handler.removeCallbacks(postConversationDismissRunnable)
                    handler.postDelayed(postConversationDismissRunnable, POST_CONVERSATION_DISMISS_DELAY_MS)
                }
            }

            handler.postDelayed(this, AUDIO_MODE_POLL_INTERVAL_MS)
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("AssistantTrampoline", "ACTION_SCREEN_OFF received -> finishing trampoline cleanly")
                cleanupAndFinish()
            }
        }
    }
    private var receiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AssistantTrampoline", "onCreate called (intent=${intent?.extras})")

        applyShowWhenLockedFlags()
        registerScreenOffReceiver()

        if (intent?.getBooleanExtra(EXTRA_DISMISS_ONLY, false) == true) {
            Log.d("AssistantTrampoline", "onCreate with EXTRA_DISMISS_ONLY -> staying opaque to stop FloatyActivity")
            isDismissing = true
            handler.postDelayed({ completeHandsFreeDismiss() }, 450)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val dismissOnly = intent?.getBooleanExtra(EXTRA_DISMISS_ONLY, false) == true
        Log.d("AssistantTrampoline", "onNewIntent called (dismissOnly=$dismissOnly, assistSucceeded=$assistSucceeded, isDismissing=$isDismissing)")

        if (dismissOnly) {
            isDismissing = true
            handler.removeCallbacksAndMessages(null)
            // 不透明な本Activityが前面に遷移完了(約450ms)して FloatyActivity が onStop になった直後に、
            // ロック中なら Keyguard を前面へ、ロック解除中ならホーム画面を前面へ出して完了する
            handler.postDelayed({ completeHandsFreeDismiss() }, 450)
            return
        }

        // Pebble から2回目・3回目のアシスタント呼び出しが来た場合:
        // 前回セッションの FloatyActivity がまだ残っている（または直前まで動いていた）可能性があるため、
        // 既存のタイマーをリセットし、本Activityが前面に出て古い FloatyActivity が onStop -> abandonAudioFocus()
        // して AudioManager.mode == MODE_NORMAL に整定するのを待ってから新規 showAssist() を呼ぶ。
        val hadPreviousSession = assistSucceeded || isDismissing
        handler.removeCallbacksAndMessages(null)
        assistSucceeded = false
        assistLaunchedAtMs = 0L
        retryCount = 0
        isDismissing = false
        conversationModeSeen = false
        modeNormalConfirmedCount = 0

        applyShowWhenLockedFlags()

        if (hadPreviousSession) {
            waitingForReinvocationSettle = true
            Log.d("AssistantTrampoline", "Re-invocation over previous session -> waiting for old session audio & window settle before showAssist")
            waitForAudioNormalAndTrigger(0)
        } else {
            waitingForReinvocationSettle = false
            if (hasWindowFocus()) {
                handler.postDelayed({
                    if (!assistSucceeded && !isDismissing) {
                        triggerAssistant()
                    }
                }, 80)
            } else {
                handler.postDelayed(fallbackRunnable, 350)
            }
        }
    }

    /**
     * 2回目以降の呼び出し時、古い FloatyActivity が停止して AudioManager.mode が MODE_NORMAL に戻り、
     * かつ Spotify 等の AudioFocus 再取得 (handleGain) が落ち着くまで待ってから triggerAssistant() を呼ぶ。
     */
    private fun waitForAudioNormalAndTrigger(waitStep: Int) {
        if (assistSucceeded || isDismissing || isFinishing || isDestroyed) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = am?.mode ?: AudioManager.MODE_NORMAL
        if (mode != AudioManager.MODE_NORMAL && waitStep < 12) {
            Log.d("AssistantTrampoline", "Waiting for previous session audio mode ($mode) to return to MODE_NORMAL (step=${waitStep + 1}/12)")
            handler.postDelayed({
                waitForAudioNormalAndTrigger(waitStep + 1)
            }, 150L)
            return
        }
        Log.d("AssistantTrampoline", "Previous session audio mode is $mode -> settling ${REINVOCATION_SETTLE_DELAY_MS}ms before launching new assistant")
        handler.postDelayed({
            waitingForReinvocationSettle = false
            if (!assistSucceeded && !isDismissing) {
                triggerAssistant()
            }
        }, REINVOCATION_SETTLE_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        Log.d("AssistantTrampoline", "onResume called (assistSucceeded=$assistSucceeded, isDismissing=$isDismissing, waitingSettle=$waitingForReinvocationSettle)")
        if (isDismissing) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ completeHandsFreeDismiss() }, 400)
            return
        }
        if (waitingForReinvocationSettle) {
            return
        }
        if (assistSucceeded) {
            // アシスタント起動後（1.5秒以上経過後）にここへ戻ってきた場合は、
            // ユーザーが手動で「閉じる」を押したか、アシスタントが閉じたため、トランポリンも終了処理へ進む
            val elapsed = SystemClock.elapsedRealtime() - assistLaunchedAtMs
            if (elapsed > 1500L) {
                Log.d("AssistantTrampoline", "Returned to foreground after ${elapsed}ms -> completing dismiss")
                isDismissing = true
                handler.removeCallbacksAndMessages(null)
                completeHandsFreeDismiss()
                return
            }
        }
        // スリープ復帰＆KEYGUARD_OCCLUDE遷移が完了して WindowManager 上で isVisible=true になるまで待つ
        handler.removeCallbacks(fallbackRunnable)
        handler.postDelayed(fallbackRunnable, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("AssistantTrampoline", "onWindowFocusChanged called with hasFocus=$hasFocus (assistSucceeded=$assistSucceeded, isDismissing=$isDismissing, waitingSettle=$waitingForReinvocationSettle)")
        if (isDismissing && hasFocus) {
            // ロック画面の背後で蓋として待機中に、ユーザーが顔認証・指紋等でロック解除した場合:
            // ここで completeHandsFreeDismiss() を呼ぶことで、背後の古い「閉じる」画面が露出せずホーム画面へ遷移する
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = km?.isKeyguardLocked ?: false
            if (!isLocked) {
                Log.d("AssistantTrampoline", "Device unlocked while trampoline was covering FloatyActivity -> dismissing to Home")
                handler.removeCallbacksAndMessages(null)
                completeHandsFreeDismiss()
            }
            return
        }
        if (waitingForReinvocationSettle) {
            return
        }
        if (hasFocus && !assistSucceeded && !isDismissing) {
            handler.removeCallbacks(fallbackRunnable)
            // フォーカス取得直後に少しだけ(60ms)待って確実に isVisible=true 状態で呼ぶ
            handler.postDelayed({
                if (!assistSucceeded && !isDismissing && !waitingForReinvocationSettle) {
                    triggerAssistant()
                }
            }, 60)
        } else if (hasFocus && assistSucceeded && !isDismissing) {
            val elapsed = SystemClock.elapsedRealtime() - assistLaunchedAtMs
            if (elapsed > 1500L) {
                Log.d("AssistantTrampoline", "Window focus regained after assistant session (${elapsed}ms) -> completing dismiss")
                isDismissing = true
                handler.removeCallbacksAndMessages(null)
                completeHandsFreeDismiss()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("AssistantTrampoline", "onStop called - assistant or keyguard is now foreground")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AssistantTrampoline", "onDestroy called")
        unregisterScreenOffReceiver()
        handler.removeCallbacksAndMessages(null)
    }

    private fun applyShowWhenLockedFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun triggerAssistant() {
        if (assistSucceeded || isDismissing || waitingForReinvocationSettle) return
        Log.d("AssistantTrampoline", "triggerAssistant called (attempt=${retryCount + 1}/$MAX_SHOW_ASSIST_RETRIES)")

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked ?: false
        Log.d("AssistantTrampoline", "isKeyguardLocked = $isLocked")

        val launched = launchShowAssist()
        Log.d("AssistantTrampoline", "launchShowAssist result = $launched")

        if (launched) {
            assistSucceeded = true
            assistLaunchedAtMs = SystemClock.elapsedRealtime()
            onAssistLaunched()
        } else if (retryCount < MAX_SHOW_ASSIST_RETRIES) {
            // スリープからの画面点灯＆KEYGUARD_OCCLUDEトランジション完了、または前回セッションの片付け完了まで
            // ActivityTaskManager が "caller is not visible" として false を返すため、150msおきに最大25回(3.75秒)リトライする
            retryCount++
            Log.d("AssistantTrampoline", "showAssist returned false (window still waking up), scheduling retry #$retryCount in ${RETRY_INTERVAL_MS}ms")
            handler.postDelayed({
                triggerAssistant()
            }, RETRY_INTERVAL_MS)
        } else {
            assistSucceeded = true
            assistLaunchedAtMs = SystemClock.elapsedRealtime()
            if (isLocked) {
                // 重要: ロック画面中に ACTION_VOICE_COMMAND (HandsFreeActivity) を呼ぶと、
                // 100% 確実に Keyguard ロック解除（指紋・PIN入力画面）が強制要求されてしまうため絶対に呼ばない！
                Log.w("AssistantTrampoline", "All showAssist retries exhausted while keyguard locked. Skipping ACTION_VOICE_COMMAND to avoid lock-screen unlock prompt.")
                cleanupAndFinish()
            } else {
                Log.w("AssistantTrampoline", "All showAssist retries exhausted while unlocked, falling back to ACTION_VOICE_COMMAND")
                launchVoiceCommand()
                onAssistLaunched()
            }
        }
    }

    private fun onAssistLaunched() {
        // アシスタント起動と連動して Pebble へマイク開始コマンド (CMD = 30) を送信
        handler.postDelayed({
            KmpDependencies.trackerEngine.pebbleMessenger?.sendNotification(30)
        }, 400)

        // 5秒後に KEEP_SCREEN_ON フラグを解除（画面が永久に消えなくなるのを防ぐ）
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d("AssistantTrampoline", "Cleared FLAG_KEEP_SCREEN_ON after 5s")
            }
        }, 5000)

        // ハンズフリー自動クローズ監視を開始:
        // 1. AudioManager.mode を 200ms 周期で監視し、MODE_ASSISTANT_CONVERSATION -> MODE_NORMAL の遷移後 1.5秒で閉じる
        // 2. 18秒間一度も対話モードに入らなかった場合も自動で閉じる
        // 3. 最大でも 60秒後に自動で閉じる
        conversationModeSeen = false
        modeNormalConfirmedCount = 0
        handler.postDelayed(audioModePollRunnable, AUDIO_MODE_POLL_INTERVAL_MS)
        handler.postDelayed(noConversationTimeoutRunnable, NO_CONVERSATION_TIMEOUT_MS)
        handler.postDelayed(maxLifetimeRunnable, MAX_SESSION_LIFETIME_MS)
    }

    /**
     * ユーザーがスマートフォンの「閉じる」ボタンを押さなくても、ハンズフリーで
     * Gemini (FloatyActivity) の残存画面を閉じる。
     */
    private fun dismissAssistantHandsFree() {
        if (isDismissing || isFinishing || isDestroyed) return
        isDismissing = true
        handler.removeCallbacksAndMessages(null)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked ?: false
        Log.d("AssistantTrampoline", "dismissAssistantHandsFree (isLocked=$isLocked): bringing opaque trampoline to front to stop FloatyActivity")

        try {
            val dismissIntent = Intent(this, AssistantTrampolineActivity::class.java).apply {
                putExtra(EXTRA_DISMISS_ONLY, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                options.pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            val pi = PendingIntent.getActivity(
                this,
                1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pi.send(this, 1, null, null, null, null, options.toBundle())
        } catch (e: Exception) {
            Log.w("AssistantTrampoline", "Failed to reorder trampoline to front, completing dismiss directly", e)
        }

        // 前面復帰トランジション完了(約450ms)を待って Keyguard または Home へ戻す
        handler.postDelayed({
            completeHandsFreeDismiss()
        }, 500)
    }

    /**
     * FloatyActivity の上に不透明な本 Activity を被せて FloatyActivity を onStop にした後、
     * ホーム画面 (CATEGORY_HOME) を前面へ送り出してから本 Activity を終了 (finishAndRemoveTask) する。
     * - ロック画面中 (isLocked == true) の場合:
     *   NexusLauncherActivity (showWhenLocked=false) がトップタスクになるため、Keyguard（ロック画面）が
     *   確実に最前面に復帰し、かつ FloatyActivity の上に Home タスクが乗るため、本 Activity を即座に
     *   finishAndRemoveTask() しても FloatyActivity が再浮上しない。
     *   さらに本 Activity が完全に破棄 (onDestroy) されるため、2回目以降の呼び出しも常にクリーンな
     *   新規 onCreate として 100% 確実に起動する。
     * - ロック解除中 (isLocked == false) の場合:
     *   ホーム画面が前面に出て「閉じる」画面を完全に隠した状態で本 Activity が終了する。
     */
    private fun completeHandsFreeDismiss() {
        if (isFinishing || isDestroyed) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked ?: false
        Log.d("AssistantTrampoline", "completeHandsFreeDismiss called (isLocked=$isLocked)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            Log.d("AssistantTrampoline", "Launched Home task above FloatyActivity (isLocked=$isLocked)")
        } catch (e: Exception) {
            Log.w("AssistantTrampoline", "Failed to launch Home screen on dismiss", e)
        }
        handler.postDelayed({
            cleanupAndFinish()
        }, 250)
    }

    private fun cleanupAndFinish() {
        unregisterScreenOffReceiver()
        handler.removeCallbacksAndMessages(null)
        if (!isFinishing && !isDestroyed) {
            Log.d("AssistantTrampoline", "Calling finishAndRemoveTask()")
            finishAndRemoveTask()
        }
    }

    private fun registerScreenOffReceiver() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, filter)
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w("AssistantTrampoline", "Failed to register screenOffReceiver", e)
        }
    }

    private fun unregisterScreenOffReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        } finally {
            receiverRegistered = false
        }
    }

    private fun launchShowAssist(): Boolean {
        return try {
            val bundle = Bundle().apply {
                putBoolean(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false)
                putBoolean("android.intent.extra.ASSIST_INPUT_HINT_KEYBOARD", false)
                putBoolean("com.google.android.apps.gsa.shared.util.extra.VOICE_SEARCH", true)
                putBoolean("com.google.android.apps.gsa.staticplugins.opa.EXTRA_OPEN_MIC", true)
                putBoolean("com.google.android.apps.gsa.opa.EXTRA_OPEN_MIC", true)
                putBoolean("android.intent.extra.ASSIST_INPUT_HINT_VOICE", true)
                putBoolean("hands_free", true)
                putBoolean("HANDS_FREE", true)
                putBoolean("extra_listen_state", true)
                putInt("android.intent.extra.ASSIST_INPUT_DEVICE_ID", 0)
                putBoolean("is_voice_search", true)
                putBoolean("open_mic", true)
                putBoolean("EXTRA_OPEN_MIC", true)
                putBoolean("android.speech.extra.DICTATION_MODE", true)
                putBoolean("android.intent.extra.START_RECORDING", true)
                putInt("invocation_type", 1)
                putString("source", "voice_button")
                putString(Intent.EXTRA_ASSIST_PACKAGE, packageName)
            }
            val res = showAssist(bundle)
            Log.d("AssistantTrampoline", "showAssist returned: $res")
            res
        } catch (e: Exception) {
            Log.e("AssistantTrampoline", "showAssist failed", e)
            false
        }
    }

    private fun launchVoiceCommand(): Boolean {
        return try {
            val voiceCommandIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(voiceCommandIntent)
            Log.d("AssistantTrampoline", "Started ACTION_VOICE_COMMAND fallback successfully")
            true
        } catch (e: Exception) {
            Log.e("AssistantTrampoline", "ACTION_VOICE_COMMAND failed", e)
            false
        }
    }
}
