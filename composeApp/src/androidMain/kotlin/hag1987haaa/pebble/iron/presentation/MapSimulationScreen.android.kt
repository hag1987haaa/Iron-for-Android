package hag1987haaa.pebble.iron.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun createAndroidImageBitmap(width: Int, height: Int, rgba: IntArray): ImageBitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(rgba, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
