package hag1987haaa.pebble.iron.presentation

import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity

interface AppActions {
    fun setActivityType(type: ActivityType)
    fun prepareTracking()  // GPS探し開始
    fun startTracking()    // ワークアウト計測開始
    fun pauseTracking()    // 一時停止
    fun resumeTracking()   // 再開
    fun finishTracking()   // 終了（確認画面へ）
    fun saveTracking()     // 保存
    fun discardTracking()  // 破棄
    fun resetTracking()    // アプリ状態をリセット
    fun syncWithHealthConnect(run: RunActivity, onComplete: (Boolean) -> Unit) // 手動で Health Connect へ同期
    fun deleteRunRecord(id: Long) // 履歴から削除 (Health Connect連動)
    fun requestHealthPermissions() // Health Connect 権限リクエスト
    fun shareRunData(run: RunActivity, format: String) // ファイルを共有 (format: "gpx" or "tcx")
    fun exportData() // 全データをエクスポート
    fun importData() // バックアップからインポート
    fun requestOverlayPermission() // 「他のアプリの上に重ねて表示」権限
    fun selectAutoExportFolder(format: String) // 自動エクスポート先フォルダ選択 (format: "gpx" or "tcx")
    fun openAutoExportFolder(format: String) // 自動エクスポート先フォルダを開く
    fun triggerAutoExport(run: RunActivity) // 自動エクスポートを実行
}
