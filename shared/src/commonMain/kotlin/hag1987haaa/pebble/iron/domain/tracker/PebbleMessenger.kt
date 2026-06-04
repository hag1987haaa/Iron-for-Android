package hag1987haaa.pebble.iron.domain.tracker

interface PebbleMessenger {
    /**
     * 定期的な統計情報（時間、距離、心拍数など）を送信する
     */
    fun sendStatistics(stats: RunStatistics)

    /**
     * 状態（STATE）の変化を通知する。状態が変わった時だけ送信する。
     */
    fun sendState(status: RunStatus)

    /**
     * 全ての最新情報を一括で同期する。Pebbleからのリクエスト（SYNC）への応答用。
     */
    fun sendFullSync(stats: RunStatistics)

    /**
     * グラフデータを送信する
     */
    fun sendGraphData(stats: RunStatistics)

    /**
     * グラフの種類を次へ切り替えて再送する（0 -> 1 -> ... -> 0）
     */
    fun rotateGraphType(stats: RunStatistics)

    /**
     * タッチ操作の設定をウォッチに送信する
     */
    fun sendTouchConfig(enabled: Boolean)

    /**
     * 通知（バイブレーション）コマンドを送信する
     * @param type 0: 距離ベース(長), 1: 時間ベース(短x2)
     */
    fun sendNotification(type: Int)

    fun launchWatchApp()
}
