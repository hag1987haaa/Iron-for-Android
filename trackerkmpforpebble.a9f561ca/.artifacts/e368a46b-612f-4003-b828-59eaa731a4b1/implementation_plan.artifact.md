# Implementation Plan - Pebble Resolution Simulator (Secret Mode)

Pebbleの解像度（144x168, 200x228, 180x180, 260x260）に応じたマップ表示をテストするための隠し画面を実装します。

## User Review Required

> [!NOTE]
> **隠しトリガー**: 設定タブの「Version xxx」テキストを長押しすることで遷移します。
>
> **解像度シミュレーション**: 画面上に各解像度の矩形領域を作成し、その中に `PlatformMapView` を描画します。これにより、ズーム感や視認性を実機を想定して確認できます。
>
> **バックジェスチャー**: 標準のシステム戻る操作で設定画面に復帰します。

## Proposed Changes

### Component: Presentation (New Screen)

#### [NEW] [MapSimulationScreen.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/commonMain/kotlin/hag1987haaa/pebble/iron/presentation/MapSimulationScreen.kt)
- 各解像度のプレビュー枠を表示する画面を新規作成します。

### Component: Navigation (App)

#### [MODIFY] [App.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/commonMain/kotlin/hag1987haaa/pebble/iron/App.kt)
- `NavHost` に `"map_simulation"` ルートを追加します。
- `SettingsScreen` および `MainScreen` 経由で遷移先を渡せるように調整します。

### Component: Settings UI (Trigger)

#### [MODIFY] [PhoneSettingsTab.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/commonMain/kotlin/hag1987haaa/pebble/iron/presentation/PhoneSettingsTab.kt)
- バージョン表示テキストに `combinedClickable` を実装し、長押しイベントを `onShowSimulation` アクションに紐付けます。

## Verification Plan

### Manual Verification
1.  **隠し画面の起動**: 設定タブの一番下にあるバージョン番号を長押しし、シミュレーション画面が開くことを確認。
2.  **解像度の確認**: 144, 200, 180, 260 の各サイズでマップが表示されていることを確認。
3.  **戻る操作**: バックジェスチャーで正しく設定画面に戻ることを確認。
