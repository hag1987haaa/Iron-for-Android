package hag1987haaa.pebble.iron.pebble

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import hag1987haaa.pebble.iron.KmpDependencies

/**
 * バックグラウンド制限を回避してデジタルアシスタント（Gemini / VoiceInteractionService）を
 * 起動するための透明なアクティビティ。
 * ロック画面の上でも表示できるように設定し、Activity.showAssist() を呼び出す。
 */
class AssistantTrampolineActivity : Activity() {
    private var assistSucceeded = false
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val fallbackRunnable = Runnable {
        if (!assistSucceeded) {
            Log.d("AssistantTrampoline", "Fallback timer fired -> triggerAssistant")
            triggerAssistant()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AssistantTrampoline", "onCreate called")

        // ロック画面を解除（指紋・顔認証要求）せず、ロック画面の「上」に画面を点灯して表示する設定
        // ※ FLAG_DISMISS_KEYGUARD や requestDismissKeyguard を呼ぶと生体認証プロンプトが強制表示されてしまうため使用しない
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("AssistantTrampoline", "onNewIntent called - resetting state for re-trigger")
        handler.removeCallbacksAndMessages(null)
        assistSucceeded = false
        retryCount = 0
        if (hasWindowFocus()) {
            handler.postDelayed({
                if (!assistSucceeded) {
                    triggerAssistant()
                }
            }, 50)
        } else {
            handler.postDelayed(fallbackRunnable, 350)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AssistantTrampoline", "onResume called")
        // スリープ復帰＆KEYGUARD_OCCLUDE遷移が完了して WindowManager 上で isVisible=true になるまで待つ
        // onWindowFocusChanged(true) が来れば即実行、来なければ 350ms 後に実行
        handler.removeCallbacks(fallbackRunnable)
        handler.postDelayed(fallbackRunnable, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("AssistantTrampoline", "onWindowFocusChanged called with hasFocus=$hasFocus")
        if (hasFocus && !assistSucceeded) {
            handler.removeCallbacks(fallbackRunnable)
            // フォーカス取得直後に少しだけ(50ms)待って確実に isVisible=true 状態で呼ぶ
            handler.postDelayed({
                if (!assistSucceeded) {
                    triggerAssistant()
                }
            }, 50)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("AssistantTrampoline", "onStop called - assistant is now foreground")
        // 重要: ここで即座に finish() すると、立ち上がりかけたアシスタント画面を
        // OSが道連れにして閉じてしまうため、finish() は遅延タイマーに任せる
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun triggerAssistant() {
        if (assistSucceeded) return
        Log.d("AssistantTrampoline", "triggerAssistant called (attempt=${retryCount + 1})")

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked ?: false
        Log.d("AssistantTrampoline", "isKeyguardLocked = $isLocked")

        val launched = launchShowAssist()
        Log.d("AssistantTrampoline", "launchShowAssist result = $launched")

        if (launched) {
            assistSucceeded = true
            onAssistLaunched()
        } else if (retryCount < 10) {
            // スリープからの画面点灯＆KEYGUARD_OCCLUDEトランジション完了（約300〜700ms）までは
            // ActivityTaskManager が "caller is not visible" として false を返すため、150msおきに最大10回リトライする
            retryCount++
            Log.d("AssistantTrampoline", "showAssist returned false (window still waking up), scheduling retry #$retryCount in 150ms")
            handler.postDelayed({
                triggerAssistant()
            }, 150)
        } else {
            Log.w("AssistantTrampoline", "All showAssist retries exhausted, falling back to ACTION_VOICE_COMMAND")
            assistSucceeded = true
            launchVoiceCommand()
            onAssistLaunched()
        }
    }

    private fun onAssistLaunched() {
        // アシスタント起動と連動して Pebble へマイク開始コマンド (CMD = 30) を送信
        // （アシスタント画面の立ち上がりラグ約400msを待って頭切れを防止）
        handler.postDelayed({
            KmpDependencies.trackerEngine.pebbleMessenger?.sendNotification(30)
        }, 400)

        // アシスタントが完全に定着した安全なタイミング（3.5秒後）で Trampoline を終了する
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                Log.d("AssistantTrampoline", "Safe delayed finish called")
                finish()
            }
        }, 3500)
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
