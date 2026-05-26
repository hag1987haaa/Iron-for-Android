package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * プラットフォーム固有のダイアログを表示するためのインターフェース
 */
interface PebblePermissionDialogProvider {
    @Composable
    fun Show(show: Boolean, onDismiss: () -> Unit)
}

/**
 * ダイアログの実装を UI 全体に提供するための Local 変数
 */
val LocalPebblePermissionDialog = staticCompositionLocalOf<PebblePermissionDialogProvider> {
    object : PebblePermissionDialogProvider {
        @Composable
        override fun Show(show: Boolean, onDismiss: () -> Unit) {
            // Android 以外では何もしない
        }
    }
}
