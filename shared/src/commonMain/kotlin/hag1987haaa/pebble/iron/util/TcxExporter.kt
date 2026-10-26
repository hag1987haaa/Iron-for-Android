package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.Instant

object TcxExporter {
    /**
     * RunActivity のデータを TCX (Training Center XML) 形式の文字列に変換します。
     * - Lap の DistanceMeters に実際の走行距離を出力し、Fitbit や Runkeeper 等での平均ペース計算崩壊を防止。
     * - 一時停止（isSegmentStart または 10秒以上のタイムスタンプギャップ）で <Track> を分割出力し、
     *   Strava, Garmin Connect, TrainingPeaks 等でポーズ時間を正確に除外。
     * - 心拍数、ケイデンス（RPM/SPM）、パワー（Watts）などのセンサーデータをTrackpointに出力。
     */
    fun export(rawRun: RunActivity): String {
        val run = rawRun.withSanitizedTimestamps()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase \n")
        sb.append("  xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" \n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n")
        sb.append("  xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\" \n")
        sb.append("  xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd http://www.garmin.com/xmlschemas/ActivityExtension/v2 http://www.garmin.com/xmlschemas/ActivityExtensionv2.xsd\">\n")

        sb.append("  <Activities>\n")
        
        val sportType = when(run.type) {
            ActivityType.RUNNING -> "Running"
            ActivityType.CYCLING -> "Biking"
            else -> "Other"
        }
        
        sb.append("    <Activity Sport=\"$sportType\">\n")
        sb.append("      <Id>${run.startTime}</Id>\n")
        
        sb.append("      <Lap StartTime=\"${run.startTime}\">\n")
        sb.append("        <TotalTimeSeconds>${run.durationSeconds}</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>${run.distanceMeters}</DistanceMeters>\n")
        
        val maxSpeed = run.route.maxOfOrNull { it.speed ?: 0.0 } ?: 0.0
        sb.append("        <MaximumSpeed>$maxSpeed</MaximumSpeed>\n")
        sb.append("        <Calories>${run.calories?.toInt() ?: 0}</Calories>\n")
        
        run.avgHeartRate?.let {
            sb.append("        <AverageHeartRateBpm><Value>${it.toInt()}</Value></AverageHeartRateBpm>\n")
        }
        run.maxHeartRate?.let {
            sb.append("        <MaximumHeartRateBpm><Value>${it.toInt()}</Value></MaximumHeartRateBpm>\n")
        }
        sb.append("        <Intensity>Active</Intensity>\n")
        run.avgCadence?.let {
            sb.append("        <Cadence>${it.toInt()}</Cadence>\n")
        }
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        
        sb.append("        <Track>\n")
        
        var prevSteps = 0
        var prevTime: Instant? = null
        val PAUSE_GAP_MS = 10_000L

        run.route.forEachIndexed { index, pt ->
            if (index > 0 && prevTime != null) {
                val timeDiffMs = pt.timestamp.toEpochMilliseconds() - prevTime!!.toEpochMilliseconds()
                if (pt.isSegmentStart || timeDiffMs >= PAUSE_GAP_MS) {
                    sb.append("        </Track>\n")
                    sb.append("        <Track>\n")
                }
            }

            sb.append("          <Trackpoint>\n")
            sb.append("            <Time>${pt.timestamp}</Time>\n")
            sb.append("            <Position>\n")
            sb.append("              <LatitudeDegrees>${pt.latitude}</LatitudeDegrees>\n")
            sb.append("              <LongitudeDegrees>${pt.longitude}</LongitudeDegrees>\n")
            sb.append("            </Position>\n")
            
            pt.altitude?.let { sb.append("            <AltitudeMeters>$it</AltitudeMeters>\n") }
            
            // 心拍数
            pt.heartRate?.let {
                if (it > 0) {
                    sb.append("            <HeartRateBpm><Value>$it</Value></HeartRateBpm>\n")
                }
            }

            // ケイデンス (センサー値優先、なければ歩数差分計算)
            val cadenceVal = pt.cadence ?: run {
                if (run.type == ActivityType.RUNNING || run.type == ActivityType.WALKING) {
                    val currentSteps = pt.steps ?: 0
                    val currentTime = pt.timestamp
                    prevTime?.let { pTime ->
                        val timeDiffSec = (currentTime.toEpochMilliseconds() - pTime.toEpochMilliseconds()) / 1000.0
                        if (timeDiffSec > 0) {
                            val stepsDiff = (currentSteps - prevSteps).coerceAtLeast(0)
                            (stepsDiff / (timeDiffSec / 60.0)).toInt().coerceIn(0, 250).takeIf { it > 0 }
                        } else null
                    }
                } else null
            }
            if (cadenceVal != null && cadenceVal > 0) {
                sb.append("            <Cadence>$cadenceVal</Cadence>\n")
            }
            if (pt.steps != null) prevSteps = pt.steps

            // Extensions: Watts (パワー) & Speed
            val hasWatts = pt.power != null && pt.power >= 0
            val hasSpeed = pt.speed != null || pt.sensorSpeed != null
            if (hasWatts || hasSpeed) {
                sb.append("            <Extensions>\n")
                sb.append("              <ns3:TPX>\n")
                val effSpeed = pt.sensorSpeed ?: pt.speed
                effSpeed?.let { sb.append("                <ns3:Speed>$it</ns3:Speed>\n") }
                if (hasWatts) {
                    sb.append("                <ns3:Watts>${pt.power}</ns3:Watts>\n")
                }
                sb.append("              </ns3:TPX>\n")
                sb.append("            </Extensions>\n")
            }

            prevTime = pt.timestamp
            sb.append("          </Trackpoint>\n")
        }

        sb.append("        </Track>\n")
        sb.append("      </Lap>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>")
        
        return sb.toString()
    }
}
