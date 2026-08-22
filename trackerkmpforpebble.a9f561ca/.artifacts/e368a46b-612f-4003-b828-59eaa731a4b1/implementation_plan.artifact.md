# Implementation Plan - Map Arrow Direction Polarity Fix

アローが「東西は正しいが南北が逆」という鏡像状態を修正するため、回転の極性を反転させます。

## User Review Required

> [!IMPORTANT]
> **修正のロジック**:
> 現在の実装では、回転が反時計回りに解釈されているため、`360 - 計算方位` を適用します。これにより、南北・東西のすべての方向が正しく一致するようになります。
>
> **一貫性の確保**:
> リアルタイムの追従時だけでなく、履歴のシーク中（過去の点を選択中）のアローについても、同じ方位修正を適用します。

## Proposed Changes

### Component: Map Rendering (Android)

#### [MODIFY] [MapView.android.kt](file:///C:/Users/1987n/AndroidStudioProjects/TrackerKMPforPebble/composeApp/src/androidMain/kotlin/hag1987haaa/pebble/iron/presentation/MapView.android.kt)
- `calculateStableBearing` の戻り値を `(360 - raw) % 360` に変更。
- `update` ブロック内の GPS 方位使用箇所にも `360 - bearing` を適用。

## Verification Plan

### Manual Verification
1.  **全方位の確認**: 北、東、南、西のそれぞれに移動した際、アローが常に進行方向を正しく指すことを確認。
2.  **履歴確認**: 履歴画面でシークバーを動かした際のアローの向きも正しいことを確認。
