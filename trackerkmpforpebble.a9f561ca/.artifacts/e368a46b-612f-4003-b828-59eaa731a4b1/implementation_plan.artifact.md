# Implementation Plan - Detailed Workout Statistics Fix

ワークアウト詳細画面のグラフ上部に表示される平均値（Avg）が、全体の統計値と一致しない問題を修正します。

## User Review Required

> [!IMPORTANT]
> **平均速度の定義**: これまではグラフ描画用に間引かれた100点の単純平均を表示していましたが、修正後は「総距離 ÷ 総時間」から算出される真の平均速度を表示します。これにより、ユーザーの計算結果と一致するようになります。

## Proposed Changes

### Component: Presentation Components

#### [MODIFY] [SimpleLineChart.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/commonMain/kotlin/hag1987haaa/pebble/iron/presentation/components/SimpleLineChart.kt)
- `SimpleLineChart` コンポーネントに `avgOverride: Float? = null` パラメータを追加。
- 渡された場合は、計算値の代わりにその値を表示。

### Component: Presentation Screens

#### [MODIFY] [DetailScreen.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/commonMain/kotlin/hag1987haaa/pebble/iron/presentation/DetailScreen.kt)
- 速度グラフ呼び出し時に、`avgOverride` として「真の平均速度」を計算して渡す。
- 心拍数グラフ呼び出し時に、保存済みの `avgHeartRate` を渡す。

## Verification Plan

### Manual Verification
1.  **平均速度の確認**: ユーザーから報告のあったデータ（6.16km, 47:35）において、Avg が約 7.7 (または 7.8) と表示されることを確認。
2.  **他グラフの確認**: 心拍数などの Avg も全体の統計と整合性が取れていることを確認。
