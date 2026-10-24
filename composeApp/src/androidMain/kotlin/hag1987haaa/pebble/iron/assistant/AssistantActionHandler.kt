package hag1987haaa.pebble.iron.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.assistant.AssistantSpeechFormatter
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.service.TrackingService

object AssistantActionHandler {
    private const val TAG = "AssistantActionHandler"

    /**
     * Google アシスタント / Gemini / Deep Link からの Intent を処理する
     * @return 処理対象の Intent だった場合は true
     */
    fun handleIntent(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false

        val data: Uri? = intent.data
        val actionExtra = intent.getStringExtra("assistant_action")
        val isIronScheme = data?.scheme == "iron" && data.host == "assistant"
        val isAssistantIntent = actionExtra != null || isIronScheme

        if (!isAssistantIntent) {
            return false
        }

        Log.d(TAG, "Handling Assistant Intent: uri=$data, actionExtra=$actionExtra")

        // 安全装置（プライバシー保護）：設定が OFF の場合は操作・データ提供を行わない
        if (!KmpDependencies.appSettings.isAssistantIntegrationEnabled) {
            Log.w(TAG, "Voice Assistant Integration is DISABLED in settings. Ignoring request.")
            Toast.makeText(
                context,
                "Iron: 音声アシスタント連携が無効です（シミュレータ画面で有効化できます）",
                Toast.LENGTH_LONG
            ).show()
            return true
        }

        // 1. アクション（START, PAUSE, RESUME, STOP, QUERY）の判定
        val targetAction = when {
            actionExtra != null -> actionExtra.uppercase()
            data != null -> {
                when (data.path?.removePrefix("/")) {
                    "start" -> "START"
                    "pause" -> "PAUSE"
                    "resume" -> "RESUME"
                    "stop" -> "STOP"
                    "query", "observe" -> "QUERY"
                    else -> null
                }
            }
            else -> null
        } ?: return false

        // 2. メトリクス照会（QUERY）の場合
        if (targetAction == "QUERY") {
            val metric = intent.getStringExtra("metric") ?: data?.getQueryParameter("metric")
            val stats = RunState.currentStats.value
            val response = AssistantSpeechFormatter.formatResponse(metric, stats, KmpDependencies.appSettings)
            Toast.makeText(context, "${response.title}: ${response.text}", Toast.LENGTH_LONG).show()
            return true
        }

        // 3. 運動種別（exercise_name）の抽出と設定
        val exerciseRaw = intent.getStringExtra("exercise_name")
            ?: data?.getQueryParameter("exercise_name")

        if (!exerciseRaw.isNullOrBlank()) {
            val resolvedType = parseActivityType(exerciseRaw)
            Log.d(TAG, "Resolved exercise '$exerciseRaw' -> $resolvedType")
            KmpDependencies.trackerEngine.setActivityType(resolvedType)
        }

        // 4. TrackingService へコマンド送信
        dispatchCommand(context, targetAction)

        // 5. ユーザーへのフィードバック
        val message = when (targetAction) {
            "START" -> "ワークアウトを開始します"
            "PAUSE" -> "ワークアウトを一時停止しました"
            "RESUME" -> "ワークアウトを再開します"
            "STOP" -> "ワークアウトを終了・保存しました"
            else -> null
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        return true
    }

    private fun parseActivityType(rawName: String): ActivityType {
        val lower = rawName.lowercase().trim()
        return when {
            // ランニング
            lower.contains("run") || lower.contains("ラン") || lower.contains("走") || lower.contains("jog") -> ActivityType.RUNNING
            // ウォーキング
            lower.contains("walk") || lower.contains("ウォーク") || lower.contains("歩") || lower.contains("散歩") -> ActivityType.WALKING
            // サイクリング
            lower.contains("cycl") || lower.contains("bike") || lower.contains("サイク") || lower.contains("自転") -> ActivityType.CYCLING
            // ハイキング
            lower.contains("hike") || lower.contains("ハイキング") || lower.contains("登") || lower.contains("trek") -> ActivityType.HIKING
            // カヤック
            lower.contains("kayak") || lower.contains("カヤック") || lower.contains("カヌー") -> ActivityType.KAYAKING
            // ボート
            lower.contains("row") || lower.contains("ボート") || lower.contains("ローイング") -> ActivityType.ROWING
            else -> ActivityType.RUNNING
        }
    }

    private fun dispatchCommand(context: Context, action: String) {
        val serviceAction = when (action) {
            "START" -> "START"
            "PAUSE" -> "PAUSE"
            "RESUME" -> "RESUME"
            "STOP" -> "SAVE_TO_RESULT"
            else -> action
        }

        Log.d(TAG, "Dispatching command to TrackingService: $serviceAction")
        try {
            val serviceIntent = Intent(context, TrackingService::class.java).apply {
                this.action = serviceAction
            }
            if (serviceAction == "STOP" || serviceAction == "SAVE_TO_RESULT" || serviceAction == "RESET") {
                context.startService(serviceIntent)
            } else {
                context.startForegroundService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to TrackingService", e)
        }
    }
}
