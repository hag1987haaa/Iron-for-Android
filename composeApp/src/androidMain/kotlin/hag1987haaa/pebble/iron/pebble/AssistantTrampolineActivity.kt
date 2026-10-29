package hag1987haaa.pebble.iron.pebble

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * バックグラウンド制限を回避してデジタルアシスタント（Gemini / VoiceInteractionService）を
 * 起動するための透明なアクティビティ。
 * ロック画面の上でも表示できるように設定し、Activity.showAssist() を呼び出す。
 */
class AssistantTrampolineActivity : Activity() {
    private var assistRequested = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AssistantTrampoline", "onCreate called")

        // ロック画面の上でも画面を点灯させて表示するように設定
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

    override fun onResume() {
        super.onResume()
        Log.d("AssistantTrampoline", "onResume called")
        // onWindowFocusChanged が万が一遅延・発火しない場合に備え、350ms 後に安全にトリガー
        handler.postDelayed({
            if (!assistRequested && !isFinishing && !isDestroyed) {
                Log.d("AssistantTrampoline", "Triggering assistant via fallback timer from onResume")
                assistRequested = true
                triggerAssistant()
            }
        }, 350)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("AssistantTrampoline", "onWindowFocusChanged called with hasFocus=$hasFocus")
        if (hasFocus && !assistRequested) {
            assistRequested = true
            triggerAssistant()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("AssistantTrampoline", "onStop called - assistant is now foreground, safe to finish trampoline")
        // アシスタントが前面に出てこのアクティビティが背面に回ったら、役目を終えたので終了
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun triggerAssistant() {
        Log.d("AssistantTrampoline", "triggerAssistant called")
        var launched = false
        try {
            // 1. Android 標準の showAssist API:
            // 音声入力（マイク自動リスニング・オープンマイク）を促すための各種パラメータを設定してリクエスト
            val bundle = Bundle().apply {
                // キーボード入力を抑制し、音声入力を最優先
                putBoolean(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false)
                putBoolean("android.intent.extra.ASSIST_INPUT_HINT_KEYBOARD", false)
                // Google アシスタント / Gemini 向け音声・マイク起動フラグ
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
                putInt("invocation_type", 1) // 1: Voice invocation
                putString("source", "voice_button")
                putString(Intent.EXTRA_ASSIST_PACKAGE, packageName)
            }
            launched = showAssist(bundle)
            Log.d("AssistantTrampoline", "showAssist returned: $launched")
        } catch (e: Exception) {
            Log.e("AssistantTrampoline", "showAssist failed", e)
        }

        if (!launched) {
            // 2. showAssist が失敗した場合（ロック画面制限など）:
            // ACTION_VOICE_COMMAND または ACTION_VOICE_ASSIST で起動
            try {
                val voiceCommandIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(voiceCommandIntent)
                Log.d("AssistantTrampoline", "Started ACTION_VOICE_COMMAND fallback")
            } catch (e: Exception) {
                try {
                    val voiceAssistIntent = Intent("android.intent.action.VOICE_ASSIST").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(voiceAssistIntent)
                    Log.d("AssistantTrampoline", "Started ACTION_VOICE_ASSIST fallback")
                } catch (e2: Exception) {
                    Log.e("AssistantTrampoline", "All assistant triggers failed", e2)
                }
            }
        }

        // 安全策タイムアウト: 万が一 onStop が来なくても 6秒後に自動終了してリソースを解放する
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                Log.d("AssistantTrampoline", "Timeout reached, finishing trampoline")
                finish()
            }
        }, 6000)
    }
}
