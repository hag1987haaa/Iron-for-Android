package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.RunActivity

object GpxExporter {
    /**
     * RunActivity のデータを GPX 1.1 形式の文字列に変換します。
     */
    fun export(run: RunActivity): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"PebbleTrackerKMP\" \n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\" \n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        
        sb.append("  <metadata>\n")
        sb.append("    <name>${run.name ?: "Workout"}</name>\n")
        sb.append("    <time>${run.startTime}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${run.name ?: "Workout"}</name>\n")
        sb.append("    <type>${run.type.name}</type>\n")
        sb.append("    <trkseg>\n")

        run.route.forEach { pt ->
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            pt.altitude?.let { sb.append("        <ele>$it</ele>\n") }
            sb.append("        <time>${pt.timestamp}</time>\n")
            // 心拍数などの拡張データが必要な場合はここに追加可能
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        
        return sb.toString()
    }
}
