package hag1987haaa.pebble.iron.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object AutoExporter {
    suspend fun execute(context: Context, run: RunActivity) = withContext(Dispatchers.IO) {
        val settings = KmpDependencies.appSettings
        
        if (settings.isAutoExportTcxEnabled) {
            settings.autoExportTcxUri?.let { uriString ->
                exportFile(context, run, "tcx", uriString)
            }
        }
        
        if (settings.isAutoExportGpxEnabled) {
            settings.autoExportGpxUri?.let { uriString ->
                exportFile(context, run, "gpx", uriString)
            }
        }
    }

    private fun exportFile(context: Context, run: RunActivity, format: String, folderUriString: String) {
        try {
            val folderUri = Uri.parse(folderUriString)
            val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
            if (rootFolder == null || !rootFolder.canWrite()) {
                Log.e("AutoExporter", "Cannot write to folder: $folderUriString")
                return
            }

            // 1. 古いファイルの削除 (IDベース)
            // 既存のエクスポートロジックがIDを付与していない場合は0Lが来る可能性があるが、
            // TrackingService側でsaveRunの後に呼ぶようにする。
            val runId = if (run.id != 0L) run.id.toString() else "new"
            val prefix = "iron_${runId}_"
            val extension = ".$format"
            
            rootFolder.listFiles().forEach { file ->
                if (file.name?.startsWith(prefix) == true && file.name?.endsWith(extension) == true) {
                    file.delete()
                }
            }

            // 2. 新しいファイル名の生成
            val localTime = run.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
            val dateStr = "${localTime.year.toString().takeLast(2)}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}"
            val timeStr = "${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"
            val typeStr = run.type.name.take(3)
            val fileName = "${prefix}${dateStr}_${timeStr}_$typeStr$extension"

            // 3. ファイルの作成と書き込み
            val mimeType = if (format == "tcx") "application/vnd.garmin.tcx+xml" else "application/gpx+xml"
            val newFile = rootFolder.createFile(mimeType, fileName)
            if (newFile != null) {
                val content = if (format == "tcx") TcxExporter.export(run) else GpxExporter.export(run)
                context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                    os.write(content.toByteArray())
                }
                Log.i("AutoExporter", "Auto export successful: $fileName")
                // 必要ならUIスレッドでToastを出すなどの処理
            }
        } catch (e: Exception) {
            Log.e("AutoExporter", "Auto export failed ($format)", e)
        }
    }
}
