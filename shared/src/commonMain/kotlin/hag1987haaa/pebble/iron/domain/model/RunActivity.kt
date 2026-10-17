package hag1987haaa.pebble.iron.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RunActivity(
    val id: Long = 0,
    val name: String? = null,
    val type: ActivityType = ActivityType.RUNNING,
    val startTime: Instant,
    val endTime: Instant? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val averagePaceSecondsPerKm: Double = 0.0,
    val calories: Double? = null,
    val steps: Int? = null,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val elevationGain: Double? = null,
    val healthConnectId: String? = null,
    val route: List<LocationPoint> = emptyList()
) {
    /**
     * GPSタイムスタンプとワークアウト開始・終了時刻の不整合（はみ出し・逆転・古いキャッシュ点の混入）を正規化します。
     * - 先頭付近に古いキャッシュ位置情報（startTimeより15秒以上前）が混入していた場合は開始時刻基準へ引き上げ
     * - ルート内のタイムスタンプが単調増加（prev <= current）になるよう保証
     * - 数秒程度のGPSチップ時刻とシステム時計のズレは、ポイント間の自然な時間間隔を潰さないよう
     *   ワークアウトの startTime / endTime 側を広げて全ポイントを [startTime, endTime] 内に完全に包含
     * - Health Connect の IntervalRecord 制約（startTime < endTime）のため最低1秒の期間を保証
     */
    fun withSanitizedTimestamps(): RunActivity {
        val fallbackDurationMs = (durationSeconds.coerceAtLeast(1L)) * 1000L
        val rawStartMs = startTime.toEpochMilliseconds()
        val rawEndMs = endTime?.toEpochMilliseconds()?.coerceAtLeast(rawStartMs + 1000L)
            ?: (rawStartMs + fallbackDurationMs)

        if (route.isEmpty()) {
            val safeEnd = Instant.fromEpochMilliseconds(rawEndMs.coerceAtLeast(rawStartMs + 1000L))
            return copy(endTime = safeEnd)
        }

        // 15秒以上前のポイントは、start() 前の古いキャッシュ位置情報（最大2分前）とみなして startTime に引き上げる。
        // それ以内（数ミリ秒〜15秒未満）のズレは GPS チップ時刻とシステム時刻の自然なオフセットとして許容する。
        val maxPreStartDriftMs = 15_000L
        val minAllowedPointMs = rawStartMs - maxPreStartDriftMs

        var prevMs = Long.MIN_VALUE
        val monotonicRoute = route.map { pt ->
            var tMs = pt.timestamp.toEpochMilliseconds()
            if (tMs < minAllowedPointMs) {
                tMs = rawStartMs
            }
            if (tMs < prevMs) {
                tMs = prevMs
            }
            prevMs = tMs
            if (tMs == pt.timestamp.toEpochMilliseconds()) {
                pt
            } else {
                pt.copy(timestamp = Instant.fromEpochMilliseconds(tMs))
            }
        }

        val firstPointMs = monotonicRoute.first().timestamp.toEpochMilliseconds()
        val lastPointMs = monotonicRoute.last().timestamp.toEpochMilliseconds()

        val effectiveStartMs = minOf(rawStartMs, firstPointMs)
        val effectiveEndMs = maxOf(rawEndMs, lastPointMs, effectiveStartMs + 1000L)

        val effectiveStart = Instant.fromEpochMilliseconds(effectiveStartMs)
        val effectiveEnd = Instant.fromEpochMilliseconds(effectiveEndMs)

        val finalRoute = monotonicRoute.map { pt ->
            val tMs = pt.timestamp.toEpochMilliseconds()
            val clampedMs = tMs.coerceIn(effectiveStartMs, effectiveEndMs)
            if (clampedMs == tMs) pt else pt.copy(timestamp = Instant.fromEpochMilliseconds(clampedMs))
        }

        return copy(
            startTime = effectiveStart,
            endTime = effectiveEnd,
            route = finalRoute
        )
    }
}

