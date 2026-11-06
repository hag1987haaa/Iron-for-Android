package hag1987haaa.pebble.iron.assistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.settings.LongPressMode

object AppFunctionManager {
    private const val TAG = "AppFunctionManager"
    private const val SERVICE_CLASS_NAME = "hag1987haaa.pebble.iron.assistant.IronAppFunctionService"

    internal val ALL_FUNCTION_IDS = listOf(
        IronAppFunctionService.FUNCTION_START_WORKOUT,
        IronAppFunctionService.FUNCTION_PAUSE_WORKOUT,
        IronAppFunctionService.FUNCTION_RESUME_WORKOUT,
        IronAppFunctionService.FUNCTION_STOP_WORKOUT,
        IronAppFunctionService.FUNCTION_GET_STATS
    )

    /**
     * アシスタント連携（App Functions / App Actions）が許可されているか判定する。
     * シミュレーション画面のトグルがON、または長押し設定で「デジタルアシスタント」が選択されている場合に有効とする。
     */
    fun isAssistantAllowed(): Boolean {
        return try {
            val s = KmpDependencies.appSettings
            val hasAssistantLongPress = s.isLongPressEnabled && (
                s.upLongPressMode == LongPressMode.ASSISTANT ||
                s.selectLongPressMode == LongPressMode.ASSISTANT ||
                s.downLongPressMode == LongPressMode.ASSISTANT
            )
            s.isAssistantIntegrationEnabled || hasAssistantLongPress
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * AppFunctionService コンポーネントを常にOSのインデクサーから見える状態（ENABLED）に保ちつつ、
     * Android 16 (API 36)+ の AppFunctionManager API で各関数の有効/無効状態を切り替える。
     * Android 15 (API 35) 以下の端末で NoClassDefFoundError / VerifyError が起きないよう Api36Impl に分離。
     */
    fun updateServiceEnabledState(context: Context, isEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < 36) return
        val effectiveEnabled = isEnabled || isAssistantAllowed()
        try {
            Api36Impl.syncState(context, effectiveEnabled)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update AppFunctionService state", t)
        }
    }

    @RequiresApi(36)
    private object Api36Impl {
        fun syncState(context: Context, effectiveEnabled: Boolean) {
            // 1. OSの AppFunctionIndexer がメタデータを読み取れるよう、Serviceコンポーネント自体は常に ENABLED に保つ
            val component = ComponentName(context, SERVICE_CLASS_NAME)
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 2. Android 16 標準の AppFunctionManager で各関数の有効/無効を切り替える
            val sysManager = context.getSystemService(android.app.appfunctions.AppFunctionManager::class.java)
            if (sysManager != null) {
                val targetState = if (effectiveEnabled) {
                    android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                } else {
                    android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                }
                val executor = context.mainExecutor
                for (fnId in ALL_FUNCTION_IDS) {
                    sysManager.setAppFunctionEnabled(
                        fnId,
                        targetState,
                        executor,
                        object : OutcomeReceiver<Void, Exception> {
                            override fun onResult(result: Void?) {
                                Log.d(TAG, "setAppFunctionEnabled($fnId) -> enabled=$effectiveEnabled")
                            }
                            override fun onError(error: Exception) {
                                Log.w(TAG, "setAppFunctionEnabled($fnId) failed: ${error.message}")
                            }
                        }
                    )
                }
            }
            Log.i(TAG, "AppFunctionService state synced: effectiveEnabled=$effectiveEnabled")
        }
    }
}
