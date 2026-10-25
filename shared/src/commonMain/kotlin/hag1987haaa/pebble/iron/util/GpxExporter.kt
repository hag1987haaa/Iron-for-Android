package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.Instant

object GpxExporter {
    /**
     * RunActivity のデータを GPX 1.1 形式の文字列に変換します。
     * - 心拍数、ケイデンス（RPM）、パワー（Watts）を標準の Garmin TrackPointExtension にて出力。
     * - 一時停止区間で <trkseg> を分割出力。
     */
    fun export(rawRun: RunActivity): String {
        val run = rawRun.withSanitizedTimestamps()
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
        val PAUSE_GAP_MS = 10_000L

        run.route.forEachIndexed { index, pt ->
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
            
            val hasHr = pt.heartRate != null && pt.heartRate > 0
            val hasCad = pt.cadence != null && pt.cadence > 0
            val effSpeed = pt.sensorSpeed ?: pt.speed
            val hasSpeed = effSpeed != null

            if (hasHr || hasCad || hasSpeed) {
                sb.append("        <extensions>\n")
                sb.append("          <gpxtpx:TrackPointExtension>\n")
                if (hasHr) {
                    sb.append("            <gpxtpx:hr>${pt.heartRate}</gpxtpx:hr>\n")
                }
                if (hasCad) {
                    sb.append("            <gpxtpx:cad>${pt.cadence}</gpxtpx:cad>\n")
                }
                if (hasSpeed) {
                    sb.append("            <gpxtpx:speed>$effSpeed</gpxtpx:speed>\n")
                }
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
