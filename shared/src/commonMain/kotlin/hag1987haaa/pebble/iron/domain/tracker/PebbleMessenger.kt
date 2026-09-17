package hag1987haaa.pebble.iron.domain.tracker

import hag1987haaa.pebble.iron.domain.model.LocationPoint

data class MapPreviewResult(
    val rgba: IntArray,
    val rleBytesCount: Int,
    val roadPixelPercent: Float
)

interface PebbleMessenger {
    /**
     * 定期的な統計情報（時間、距離、心拍数など）を送信する
     */
    fun sendStatistics(stats: RunStatistics)

    /**
     * 状態（STATE）の変化を通知する。
     */
    fun sendState(status: RunStatus, stats: RunStatistics)

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
     * 中段の表示項目を次へ切り替えて再送する
     */
    fun rotateMidData(stats: RunStatistics)

    /**
     * タッチ操作の設定をウォッチに送信する
     */
    fun sendTouchConfig(enabled: Boolean)

    /**
     * 通知（バイブレーション）コマンドを送信する
     * @param type 0: 距離ベース(長), 1: 時間ベース(短x2)
     */
    fun sendNotification(type: Int)

    /**
     * 下段の表示データ文字列を送信する
     */
    fun sendLowerData(lowerDataString: String)

    /**
     * 中段の表示項目IDを送信する
     */
    fun sendMidId(id: Int)

    /**
     * 下段の表示項目IDを送信する
     */
    fun sendLowerId(id: Int)

    /**
     * マップの表示状態を送信する
     */
    fun sendMapState(isActive: Boolean)

    /**
     * マップ描画用のデータチャンクを送信する
     */
    fun sendMapChunk(data: ByteArray, chunkIdx: Int, totalChunks: Int)

    /**
     * 現在の中段表示項目IDを設定する（ウォッチからの同期用）
     */
    fun setCurrentMidId(id: Int)

    /**
     * 現在の下段表示項目IDを設定する（ウォッチからの同期用）
     */
    fun setCurrentLowerId(id: Int)

    /**
     * マップの表示状態を設定する（ウォッチからの同期用）
     */
    fun setMapState(isActive: Boolean)

    /**
     * マップが表示中かどうか
     */
    val isMapActive: Boolean get() = false

    /**
     * マップズームイン指示
     */
    fun zoomInMap() {}

    /**
     * マップズームアウト指示
     */
    fun zoomOutMap() {}

    /**
     * マップ現在地センタリング指示
     */
    fun recenterMap() {}

    /**
     * マップパン（スクロール）指示
     */
    fun panMap(dx: Int, dy: Int) {}

    /**
     * マップ中心位置化・拡大率初期化・ノースアップ/ノーズアップ切替指示
     */
    fun resetAndToggleMapOrientation() {}

    /**
     * マップ自動終了タイマーをリセット（延長）する
     */
    fun resetMapAutoCloseTimer() {}

    /**
     * マップの初期ズームレベルを設定する
     */
    fun setMapZoom(zoom: Int) {}

    /**
     * マップデータを送信する（経路情報を元にビットマップ生成・RLEエンコード・分割送信を一括で行う）
     */
    fun sendMap(points: List<LocationPoint>, width: Int, height: Int)

    fun launchWatchApp()

    fun requestWatchInfo()

    suspend fun getMapPreviewRgba(points: List<LocationPoint>, width: Int, height: Int, isMonochrome: Boolean, zoom: Int? = null): IntArray? = null

    suspend fun getMapPreviewInfo(points: List<LocationPoint>, width: Int, height: Int, isMonochrome: Boolean, zoom: Int? = null): MapPreviewResult? = null

    /**
     * 予定ルート（GPXなど）を設定・解除する
     */
    fun setPlannedCourse(points: List<LocationPoint>?) {}

    /**
     * コース一覧データ文字列（例: "1,NAME1|0,NAME2"）をウォッチに一括送信する
     */
    fun sendCoursesData(coursesDataString: String) {}
}
