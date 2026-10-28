package hag1987haaa.pebble.iron.assistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object AppFunctionManager {
    private const val TAG = "AppFunctionManager"
    private const val SERVICE_CLASS_NAME = "hag1987haaa.pebble.iron.assistant.IronAppFunctionService"

    /**
     * AndroidManifest 上の AppFunctionService コンポーネントの有効/無効を切り替える。
     * Android 16 (API 36)+ でのみ動作し、それ未満の OS ではクラス参照を含め一切実行しない。
     */
    fun updateServiceEnabledState(context: Context, isEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < 36) return
        try {
            val component = ComponentName(context, SERVICE_CLASS_NAME)
            val newState = if (isEnabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "AppFunctionService component state updated: isEnabled=$isEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update AppFunctionService component state", e)
        }
    }
}
