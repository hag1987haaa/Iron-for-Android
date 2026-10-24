package hag1987haaa.pebble.iron.domain.assistant

import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.domain.tracker.RunStatus

data class AssistantSpeechResponse(
    val title: String,
    val text: String,
    val ttsSpeech: String
)

object AssistantSpeechFormatter {

    fun formatResponse(
        metric: String?,
        stats: RunStatistics,
        settings: AppSettings
    ): AssistantSpeechResponse {
        // 1. 安全装置（プライバシー保護）：設定が OFF なら生体データや計測値を返さない
        if (!settings.isAssistantIntegrationEnabled) {
            return AssistantSpeechResponse(
                title = "音声連携が無効です",
                text = "Ironの設定で音声アシスタント連携が無効になっています。",
                ttsSpeech = "Ironで音声アシスタント連携が無効になっています。設定画面から有効にしてください。"
            )
        }

        // 2. ワークアウトが実行中でない場合
        val isTracking = stats.status == RunStatus.ACTIVE || stats.status == RunStatus.PAUSED
        if (!isTracking) {
            return AssistantSpeechResponse(
                title = "計測停止中",
                text = "現在ワークアウトは計測されていません。",
                ttsSpeech = "現在ワークアウトは計測されていません。"
            )
        }

        val query = metric?.lowercase()?.trim() ?: ""

        // 3. クエリに応じたセリフ生成
        return when {
            // ペース
            query.contains("pace") || query.contains("ペース") || query.contains("スピード") || query.contains("速さ") -> {
                val pace = stats.formattedPace
                AssistantSpeechResponse(
                    title = "現在のペース",
                    text = "$pace /km",
                    ttsSpeech = "現在のペースは、1キロあたり $pace です。"
                )
            }

            // 心拍数
            query.contains("heart") || query.contains("心拍") || query.contains("脈") || query.contains("hr") -> {
                val hr = stats.currentHeartRate
                val minZone = settings.getHrZoneMin(stats.activityType)
                val maxZone = settings.getHrZoneMax(stats.activityType)

                if (hr != null && hr > 0) {
                    val zoneStatus = when {
                        hr < minZone -> "目標より低めです（目標: $minZone 〜 $maxZone）"
                        hr > maxZone -> "目標より高めです（目標: $minZone 〜 $maxZone）"
                        else -> "目標ゾーン内です"
                    }
                    AssistantSpeechResponse(
                        title = "現在の心拍数",
                        text = "$hr bpm ($zoneStatus)",
                        ttsSpeech = "現在の心拍数は $hr です。$zoneStatus。"
                    )
                } else {
                    AssistantSpeechResponse(
                        title = "現在の心拍数",
                        text = "計測中...",
                        ttsSpeech = "心拍数は現在取得できていません。"
                    )
                }
            }

            // 目標心拍数（ゾーン）
            query.contains("target") || query.contains("目標") || query.contains("ゾーン") -> {
                val minZone = settings.getHrZoneMin(stats.activityType)
                val maxZone = settings.getHrZoneMax(stats.activityType)
                AssistantSpeechResponse(
                    title = "目標心拍数ゾーン",
                    text = "$minZone - $maxZone bpm",
                    ttsSpeech = "目標心拍数ゾーンは、$minZone から $maxZone です。"
                )
            }

            // 走行距離
            query.contains("dist") || query.contains("距離") || query.contains("キロ") -> {
                val dist = stats.formattedDistance
                AssistantSpeechResponse(
                    title = "走行距離",
                    text = "$dist km",
                    ttsSpeech = "現在の走行距離は、$dist キロメートルです。"
                )
            }

            // 経過時間
            query.contains("time") || query.contains("時間") || query.contains("分") || query.contains("秒") -> {
                val time = stats.formattedTime
                AssistantSpeechResponse(
                    title = "経過時間",
                    text = time,
                    ttsSpeech = "経過時間は、$time です。"
                )
            }

            // デフォルト（サマリー）
            else -> {
                val time = stats.formattedTime
                val dist = stats.formattedDistance
                val pace = stats.formattedPace
                val hr = stats.currentHeartRate

                val hrText = if (hr != null && hr > 0) "、心拍数は $hr" else ""
                val hrDisp = if (hr != null && hr > 0) "$hr bpm" else "-- bpm"

                AssistantSpeechResponse(
                    title = "ワークアウト状況",
                    text = "時間: $time | 距離: $dist km | ペース: $pace | 心拍: $hrDisp",
                    ttsSpeech = "経過時間 $time、距離 $dist キロ、ペース $pace$hrText です。"
                )
            }
        }
    }
}
