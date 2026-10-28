package hag1987haaa.pebble.iron.assistant

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.assistant.AssistantSpeechFormatter
import hag1987haaa.pebble.iron.domain.tracker.RunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android 16 (API 36)+ 向けの App Functions サービス実装。
 * Gemini / Google アシスタント等の AI エージェントからのオンデバイス機能呼び出しを処理します。
 *
 * 【プライバシー設計】
 * 1. 初期状態では AndroidManifest 上で android:enabled="false" に設定されており、OSのレジストリから隠蔽。
 * 2. シミュレーション画面でユーザーが有効化した時のみ PackageManager 経由で有効化。
 * 3. 実行時にも必ず AppSettings.isAssistantIntegrationEnabled を検査し、無効時は即座に遮断。
 */
@RequiresApi(36)
class IronAppFunctionService : AppFunctionService() {

    private class DocBuilder(namespace: String, id: String, schemaType: String) :
        GenericDocument.Builder<DocBuilder>(namespace, id, schemaType)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
    ) {
        val functionId = request.functionIdentifier
        Log.d(TAG, "onExecuteFunction: id=$functionId from $callingPackage")

        // 1. 安全装置（プライバシー保護）：設定が OFF の場合は即座にアクセス拒否
        if (!KmpDependencies.appSettings.isAssistantIntegrationEnabled) {
            Log.w(TAG, "App Functions is DISABLED in settings. Rejecting request.")
            callback.onError(
                AppFunctionException(
                    AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                    "Iron: App Functions integration is disabled by user in settings."
                )
            )
            return
        }

        // 2. メインスレッドをブロックしないよう非同期で処理
        serviceScope.launch {
            try {
                when (functionId) {
                    FUNCTION_START_WORKOUT -> {
                        val exerciseName = request.parameters.getPropertyString("exercise_name")
                        if (!exerciseName.isNullOrBlank()) {
                            val resolvedType = AssistantActionHandler.parseActivityType(exerciseName)
                            KmpDependencies.trackerEngine.setActivityType(resolvedType)
                        }
                        AssistantActionHandler.dispatchCommand(applicationContext, "START")

                        val resultDoc = DocBuilder("", "start", "WorkoutActionResult")
                            .setPropertyString("status", "STARTED")
                            .setPropertyString("message", "ワークアウトを開始しました")
                            .build()
                        callback.onResult(ExecuteAppFunctionResponse(resultDoc))
                    }
                    FUNCTION_PAUSE_WORKOUT -> {
                        AssistantActionHandler.dispatchCommand(applicationContext, "PAUSE")
                        val resultDoc = DocBuilder("", "pause", "WorkoutActionResult")
                            .setPropertyString("status", "PAUSED")
                            .setPropertyString("message", "ワークアウトを一時停止しました")
                            .build()
                        callback.onResult(ExecuteAppFunctionResponse(resultDoc))
                    }
                    FUNCTION_RESUME_WORKOUT -> {
                        AssistantActionHandler.dispatchCommand(applicationContext, "RESUME")
                        val resultDoc = DocBuilder("", "resume", "WorkoutActionResult")
                            .setPropertyString("status", "RESUMED")
                            .setPropertyString("message", "ワークアウトを再開しました")
                            .build()
                        callback.onResult(ExecuteAppFunctionResponse(resultDoc))
                    }
                    FUNCTION_STOP_WORKOUT -> {
                        AssistantActionHandler.dispatchCommand(applicationContext, "STOP")
                        val resultDoc = DocBuilder("", "stop", "WorkoutActionResult")
                            .setPropertyString("status", "STOPPED")
                            .setPropertyString("message", "ワークアウトを終了・保存しました")
                            .build()
                        callback.onResult(ExecuteAppFunctionResponse(resultDoc))
                    }
                    FUNCTION_GET_STATS -> {
                        val metric = request.parameters.getPropertyString("metric")
                        val stats = RunState.currentStats.value
                        val speech = AssistantSpeechFormatter.formatResponse(
                            metric, stats, KmpDependencies.appSettings
                        )
                        val resultDoc = DocBuilder("", "stats", "WorkoutStatsResult")
                            .setPropertyString("title", speech.title)
                            .setPropertyString("text", speech.text)
                            .setPropertyString("speech", speech.ttsSpeech)
                            .setPropertyString("pace", stats.formattedPace)
                            .setPropertyString("distance", stats.formattedDistance)
                            .setPropertyString("time", stats.formattedTime)
                            .setPropertyLong("heartRate", stats.currentHeartRate?.toLong() ?: 0L)
                            .build()
                        callback.onResult(ExecuteAppFunctionResponse(resultDoc))
                    }
                    else -> {
                        callback.onError(
                            AppFunctionException(
                                AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                                "Unknown function identifier: $functionId"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing function: $functionId", e)
                callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                        e.message ?: "Unknown error"
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "IronAppFunctionService"

        const val FUNCTION_START_WORKOUT = "startWorkout"
        const val FUNCTION_PAUSE_WORKOUT = "pauseWorkout"
        const val FUNCTION_RESUME_WORKOUT = "resumeWorkout"
        const val FUNCTION_STOP_WORKOUT = "stopWorkout"
        const val FUNCTION_GET_STATS = "getWorkoutStats"

        /**
         * AndroidManifest 上のサービスコンポーネントの有効/無効を切り替える
         */
        fun updateServiceEnabledState(context: Context, isEnabled: Boolean) {
            AppFunctionManager.updateServiceEnabledState(context, isEnabled)
        }
    }
}
