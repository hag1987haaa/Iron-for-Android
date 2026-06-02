package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import kotlinx.datetime.Instant

object TcxExporter {
    /**
     * RunActivity のデータを TCX (Training Center XML) 形式の文字列に変換します。
     */
    fun export(run: RunActivity): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<TrainingCenterDatabase \n")
        sb.append("  xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" \n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n")
        sb.append("  xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">\n")

        sb.append("  <Activities>\n")
        
        val sportType = when(run.type) {
            ActivityType.RUNNING -> "Running"
            ActivityType.CYCLING -> "Biking"
            else -> "Other"
        }
        
        sb.append("    <Activity Sport=\"$sportType\">\n")
        sb.append("      <Id>${run.startTime}</Id>\n")
        
        // 1つの Lap として記録
        sb.append("      <Lap StartTime=\"${run.startTime}\">\n")
        sb.append("        <TotalTimeSeconds>${run.durationSeconds}</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>${run.distanceMeters}</DistanceMeters>\n")
        
        // アプリ側で計算済みの最高速度やカロリーをセット
        val maxSpeed = run.route.maxOfOrNull { it.speed ?: 0.0 } ?: 0.0
        sb.append("        <MaximumSpeed>$maxSpeed</MaximumSpeed>\n")
        sb.append("        <Calories>${run.calories?.toInt() ?: 0}</Calories>\n")
        
        // 平均心拍数
        run.avgHeartRate?.let {
            sb.append("        <AverageHeartRateBpm><Value>${it.toInt()}</Value></AverageHeartRateBpm>\n")
        }
        
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        
        sb.append("        <Track>\n")
        
        var prevSteps = 0
        var prevTime: Instant? = null

        run.route.forEach { pt ->
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

            // ケイデンス（ランニング・ウォーキングのみ）
            if (run.type == ActivityType.RUNNING || run.type == ActivityType.WALKING) {
                val currentSteps = pt.steps ?: 0
                val currentTime = pt.timestamp
                
                prevTime?.let { pTime ->
                    val timeDiffSec = (currentTime.toEpochMilliseconds() - pTime.toEpochMilliseconds()) / 1000.0
                    if (timeDiffSec > 0) {
                        val stepsDiff = (currentSteps - prevSteps).coerceAtLeast(0)
                        // 分速（SPM）に変換
                        val spm = (stepsDiff / (timeDiffSec / 60.0)).toInt().coerceIn(0, 250)
                        if (spm > 0) {
                            sb.append("            <Cadence>$spm</Cadence>\n")
                        }
                    }
                }
                prevSteps = currentSteps
                prevTime = currentTime
            }
            
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
