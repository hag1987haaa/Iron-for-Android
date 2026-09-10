package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.Instant

object GpxExporter {
    /**
     * RunActivity のデータを GPX 1.1 形式の文字列に変換します。
     * 外部プラットフォームでの互換性と正確性を高めるため、座標と時刻のみを基本とし、
     * 累積距離のタグは含めません。心拍数等は標準的な拡張形式で追加します。
     * 
     * 一時停止区間（isSegmentStart または 10秒以上のタイムスタンプギャップ）で
     * <trkseg> を分割出力し、Strava, Garmin Connect 等での正確なポーズ時間除外・平均ペース計算を実現します。
     */
    fun export(run: RunActivity): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"PebbleTrackerKMP\" \n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\" \n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n")
        sb.append("     xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" \n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">\n")
        
        sb.append("  <metadata>\n")
        sb.append("    <name>${run.name ?: "Workout"}</name>\n")
        sb.append("    <time>${run.startTime}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${run.name ?: "Workout"}</name>\n")
        sb.append("    <type>${run.type.name}</type>\n")
        sb.append("    <trkseg>\n")

        var prevTime: Instant? = null
        val PAUSE_GAP_MS = 10_000L // 10秒以上のタイムスタンプギャップでポーズ判定 (過去データ救済対応)

        run.route.forEachIndexed { index, pt ->
            // 一時停止区間の判定: 再開フラグまたは10秒以上のタイムスタンプギャップでトラックセグメントを分割
            if (index > 0 && prevTime != null) {
                val timeDiffMs = pt.timestamp.toEpochMilliseconds() - prevTime!!.toEpochMilliseconds()
                if (pt.isSegmentStart || timeDiffMs >= PAUSE_GAP_MS) {
                    sb.append("    </trkseg>\n")
                    sb.append("    <trkseg>\n")
                }
            }

            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            pt.altitude?.let { sb.append("        <ele>$it</ele>\n") }
            sb.append("        <time>${pt.timestamp}</time>\n")
            
            // 心拍数などの拡張データ
            if (pt.heartRate != null && pt.heartRate > 0) {
                sb.append("        <extensions>\n")
                sb.append("          <gpxtpx:TrackPointExtension>\n")
                sb.append("            <gpxtpx:hr>${pt.heartRate}</gpxtpx:hr>\n")
                sb.append("          </gpxtpx:TrackPointExtension>\n")
                sb.append("        </extensions>\n")
            }

            sb.append("      </trkpt>\n")
            prevTime = pt.timestamp
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        
        return sb.toString()
    }
}
