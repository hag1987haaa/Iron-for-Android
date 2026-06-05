package hag1987haaa.pebble.iron.pebble

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * バックグラウンド制限を回避してアシスタントを起動するための透明なアクティビティ。
 * ロック画面の上でも表示できるように設定し、ここからアシスタントを呼び出す。
 */
class AssistantTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面の上でも動作するように設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        try {
            val assistantIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(assistantIntent)
        } catch (e: Exception) {
            android.util.Log.e("AssistantTrampoline", "Failed to launch assistant", e)
        } finally {
            // アシスタントを呼んだら即座にこの画面を閉じる
            finish()
        }
    }
}
