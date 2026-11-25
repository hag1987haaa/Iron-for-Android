package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import org.jetbrains.compose.resources.stringResource

@Composable
fun WatchSettingsTab(viewModel: SettingsViewModel, actions: AppActions) {
    val hrInterval by viewModel.hrSamplingInterval.collectAsState()
    val notifDistanceStep by viewModel.notifDistanceStep.collectAsState()
    val notifTime by viewModel.notifTime.collectAsState()
    val isAutoLaunchDistEnabled by viewModel.isAutoLaunchDistEnabled.collectAsState()
    val isAutoLaunchTimeEnabled by viewModel.isAutoLaunchTimeEnabled.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    var isNotifExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        // 1. ウォッチ通知（共通）
        SettingsSectionHeader(stringResource(Res.string.settings_section_common))
        ExpandableSubSection(
            title = stringResource(Res.string.settings_section_notification),
            expanded = isNotifExpanded,
            onToggle = { isNotifExpanded = !isNotifExpanded }
        ) {
            NotificationSettingsContent(
                notifDistanceStep = notifDistanceStep,
                time = notifTime,
                launchDist = isAutoLaunchDistEnabled,
                launchTime = isAutoLaunchTimeEnabled,
                isMetric = isMetric,
                viewModel = viewModel
            )
        }

        // 2. 心拍サンプリング設定
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_hr_interval))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(Res.string.settings_hr_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                HrIntervalSelector(hrInterval) { viewModel.updateHrSamplingInterval(it) }
            }
        }

        // 3. マップ表示・操作設定（共通）
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_map))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            MapSettingsContent(viewModel)
        }

        // 4. デバイス連携
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_device))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            val (showPebbleDialog, setShowPebbleDialog) = remember { mutableStateOf(false) }
            LocalPebblePermissionDialog.current.Show(show = showPebbleDialog, onDismiss = { setShowPebbleDialog(false) })
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_pebble_app_title)) },
                supportingContent = { Text(stringResource(Res.string.settings_pebble_app_desc)) },
                leadingContent = { Icon(Icons.Default.Watch, null) },
                trailingContent = { TextButton(onClick = { setShowPebbleDialog(true) }) { Text(stringResource(Res.string.settings_button_configure)) } }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun NotificationSettingsContent(
    notifDistanceStep: Float,
    time: Int,
    launchDist: Boolean,
    launchTime: Boolean,
    isMetric: Boolean,
    viewModel: SettingsViewModel
) {
    val isDistVibEnabled by viewModel.isDistNotificationVibrationEnabled.collectAsState()
    val distAutoShowMapSec by viewModel.autoShowMapAfterDistNotificationSeconds.collectAsState()

    val isTimeVibEnabled by viewModel.isTimeNotificationVibrationEnabled.collectAsState()
    val timeAutoShowMapSec by viewModel.autoShowMapAfterTimeNotificationSeconds.collectAsState()

    Column(modifier = Modifier.padding(12.dp)) {
        Text(text = stringResource(Res.string.settings_notif_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))

        // --- 距離通知 (オートラップ) ---
        Text(
            text = stringResource(Res.string.settings_notif_distance_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.settings_notif_distance_label), style = MaterialTheme.typography.bodyMedium)
            var expanded by remember { mutableStateOf(false) }

            val unitLabel = if (isMetric) "km" else "mi"
            val displayValue = if (notifDistanceStep == 0.0f) {
                stringResource(Res.string.settings_notif_off)
            } else {
                "$notifDistanceStep $unitLabel"
            }

            Box {
                TextButton(onClick = { expanded = true }) { Text(displayValue) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    val steps = listOf(0.0, 0.5, 1.0, 2.0, 5.0, 10.0)
                    steps.forEach { step ->
                        val label = if (step == 0.0) {
                            stringResource(Res.string.settings_notif_off)
                        } else {
                            "$step $unitLabel"
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { viewModel.updateNotifDistanceStep(step); expanded = false }
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.settings_notif_distance_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Switch(checked = launchDist, onCheckedChange = { viewModel.updateAutoLaunchDistEnabled(it) }, modifier = Modifier.scale(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_vibration_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_vibration_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isDistVibEnabled, onCheckedChange = { viewModel.updateDistNotificationVibrationEnabled(it) }, modifier = Modifier.scale(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_auto_show_map_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_auto_show_map_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            var expanded by remember { mutableStateOf(false) }
            val displayValue = when (distAutoShowMapSec) {
                0 -> stringResource(Res.string.settings_notif_auto_show_map_off)
                3 -> stringResource(Res.string.settings_notif_auto_show_map_3s)
                5 -> stringResource(Res.string.settings_notif_auto_show_map_5s)
                10 -> stringResource(Res.string.settings_notif_auto_show_map_10s)
                else -> "${distAutoShowMapSec}s"
            }
            Box {
                TextButton(onClick = { expanded = true }) { Text(displayValue) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        0 to stringResource(Res.string.settings_notif_auto_show_map_off),
                        3 to stringResource(Res.string.settings_notif_auto_show_map_3s),
                        5 to stringResource(Res.string.settings_notif_auto_show_map_5s),
                        10 to stringResource(Res.string.settings_notif_auto_show_map_10s)
                    ).forEach { (sec, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateAutoShowMapAfterDistNotificationSeconds(sec)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // --- 時間通知 (インターバル) ---
        Text(
            text = stringResource(Res.string.settings_notif_time_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.settings_notif_time_label), style = MaterialTheme.typography.bodyMedium)
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) { Text(if (time == 0) stringResource(Res.string.settings_notif_off) else "${time / 60} min") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(0, 60, 300, 600, 900, 1800, 3600).forEach { s ->
                        DropdownMenuItem(text = { Text(if (s == 0) stringResource(Res.string.settings_notif_off) else "${s / 60} min") }, onClick = { viewModel.updateNotifTime(s); expanded = false })
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.settings_notif_time_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Switch(checked = launchTime, onCheckedChange = { viewModel.updateAutoLaunchTimeEnabled(it) }, modifier = Modifier.scale(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_vibration_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_vibration_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isTimeVibEnabled, onCheckedChange = { viewModel.updateTimeNotificationVibrationEnabled(it) }, modifier = Modifier.scale(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_auto_show_map_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_auto_show_map_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            var expanded by remember { mutableStateOf(false) }
            val displayValue = when (timeAutoShowMapSec) {
                0 -> stringResource(Res.string.settings_notif_auto_show_map_off)
                3 -> stringResource(Res.string.settings_notif_auto_show_map_3s)
                5 -> stringResource(Res.string.settings_notif_auto_show_map_5s)
                10 -> stringResource(Res.string.settings_notif_auto_show_map_10s)
                else -> "${timeAutoShowMapSec}s"
            }
            Box {
                TextButton(onClick = { expanded = true }) { Text(displayValue) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        0 to stringResource(Res.string.settings_notif_auto_show_map_off),
                        3 to stringResource(Res.string.settings_notif_auto_show_map_3s),
                        5 to stringResource(Res.string.settings_notif_auto_show_map_5s),
                        10 to stringResource(Res.string.settings_notif_auto_show_map_10s)
                    ).forEach { (sec, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateAutoShowMapAfterTimeNotificationSeconds(sec)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // --- 心拍ゾーン通知（共通設定） ---
        Text(
            text = stringResource(Res.string.settings_notif_hr_zone_common_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(Res.string.settings_notif_hr_zone_common_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val hrZoneOutDuration by viewModel.hrZoneOutDurationSeconds.collectAsState()
        val hrZoneRepeatInterval by viewModel.hrZoneRepeatIntervalSeconds.collectAsState()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_hr_zone_out_duration), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_hr_zone_out_duration_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            var durationExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { durationExpanded = true }) {
                    Text("${hrZoneOutDuration}s")
                }
                DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                    listOf(5, 10, 15, 20, 30, 45, 60).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text("${sec}s") },
                            onClick = {
                                viewModel.updateHrZoneOutDurationSeconds(sec)
                                durationExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_notif_hr_zone_repeat_interval), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_notif_hr_zone_repeat_interval_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            var repeatExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { repeatExpanded = true }) {
                    Text(if (hrZoneRepeatInterval == 0) stringResource(Res.string.settings_notif_off) else "${hrZoneRepeatInterval}s")
                }
                DropdownMenu(expanded = repeatExpanded, onDismissRequest = { repeatExpanded = false }) {
                    listOf(0, 5, 10, 15, 20, 30, 45, 60).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text(if (sec == 0) stringResource(Res.string.settings_notif_off) else "${sec}s") },
                            onClick = {
                                viewModel.updateHrZoneRepeatIntervalSeconds(sec)
                                repeatExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MapSettingsContent(viewModel: SettingsViewModel) {
    val isAutoShowMapOnReady by viewModel.isAutoShowMapOnReadyEnabled.collectAsState()
    val autoCloseTimeout by viewModel.mapAutoCloseTimeoutSeconds.collectAsState()
    val isMapSwipePan by viewModel.isMapSwipePanEnabled.collectAsState()
    val isMapHeadingUp by viewModel.isMapHeadingUp.collectAsState()

    val mapRouteColor by viewModel.mapRouteColor.collectAsState()
    val mapPlannedColor by viewModel.mapPlannedColor.collectAsState()
    val mapLocationColor by viewModel.mapLocationColor.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(Res.string.settings_map_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 1. マップ自動表示（準備完了時）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_map_auto_show_ready_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_map_auto_show_ready_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = isAutoShowMapOnReady,
                onCheckedChange = { viewModel.updateAutoShowMapOnReadyEnabled(it) },
                modifier = Modifier.scale(0.7f)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 2. マップ自動終了タイマー
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_map_auto_close_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_map_auto_close_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            var expanded by remember { mutableStateOf(false) }
            val displayValue = "${autoCloseTimeout}s"
            Box {
                TextButton(onClick = { expanded = true }) { Text(displayValue) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        5 to "5s",
                        10 to "10s",
                        15 to "15s",
                        30 to "30s"
                    ).forEach { (sec, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateMapAutoCloseTimeoutSeconds(sec)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 3. マップ表示中のスワイプ操作
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_map_swipe_pan_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_map_swipe_pan_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = isMapSwipePan,
                onCheckedChange = { viewModel.updateMapSwipePanEnabled(it) },
                modifier = Modifier.scale(0.7f)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 3-B. マップの向き設定（デフォルト）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_map_heading_up_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.settings_map_heading_up_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = isMapHeadingUp,
                onCheckedChange = { viewModel.updateMapHeadingUp(it) },
                modifier = Modifier.scale(0.7f)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 4. マップ描画色設定ヘッダー
        Text(
            text = stringResource(Res.string.settings_map_colors_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_route_color_title),
            description = stringResource(Res.string.settings_map_route_color_desc),
            currentColor = mapRouteColor,
            onColorSelected = { viewModel.updateMapRouteColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_planned_color_title),
            description = stringResource(Res.string.settings_map_planned_color_desc),
            currentColor = mapPlannedColor,
            onColorSelected = { viewModel.updateMapPlannedColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_location_color_title),
            description = stringResource(Res.string.settings_map_location_color_desc),
            currentColor = mapLocationColor,
            onColorSelected = { viewModel.updateMapLocationColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
        Text(
            text = "Map Data: \u00a9 CARTO, \u00a9 OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun getMapColorName(color: MapColorPreset): String {
    return when (color) {
        MapColorPreset.RED -> stringResource(Res.string.map_color_red)
        MapColorPreset.MAGENTA -> stringResource(Res.string.map_color_magenta)
        MapColorPreset.ORANGE -> stringResource(Res.string.map_color_orange)
        MapColorPreset.YELLOW -> stringResource(Res.string.map_color_yellow)
        MapColorPreset.GREEN -> stringResource(Res.string.map_color_green)
        MapColorPreset.CYAN -> stringResource(Res.string.map_color_cyan)
        MapColorPreset.BLUE -> stringResource(Res.string.map_color_blue)
    }
}

@Composable
private fun MapColorSettingRow(
    title: String,
    description: String,
    currentColor: MapColorPreset,
    onColorSelected: (MapColorPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(currentColor.argbColor), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(getMapColorName(currentColor), style = MaterialTheme.typography.bodySmall)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MapColorPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(preset.argbColor), CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(getMapColorName(preset), style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        onClick = {
                            onColorSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
