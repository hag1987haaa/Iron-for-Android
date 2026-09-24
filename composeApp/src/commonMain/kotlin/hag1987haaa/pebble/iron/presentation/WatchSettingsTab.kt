package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import hag1987haaa.pebble.iron.domain.model.ActivityType
import androidx.compose.ui.text.font.FontWeight

@Composable
fun WatchSettingsTab(viewModel: SettingsViewModel, actions: AppActions) {
    val hrInterval by viewModel.hrSamplingInterval.collectAsState()
    val notifDistanceStep by viewModel.notifDistanceStep.collectAsState()
    val notifTime by viewModel.notifTime.collectAsState()
    val isAutoLaunchDistEnabled by viewModel.isAutoLaunchDistEnabled.collectAsState()
    val isAutoLaunchTimeEnabled by viewModel.isAutoLaunchTimeEnabled.collectAsState()
    val enabledMidItems by viewModel.enabledMidTypes.collectAsState()
    val enabledLowerItems by viewModel.enabledLowerTypes.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()

    var isNotifExpanded by remember { mutableStateOf(false) }
    var isMidDataExpanded by remember { mutableStateOf(false) }
    var isLowerDataExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        // 1. エクササイズ設定
        SettingsSectionHeader(stringResource(Res.string.settings_section_exercise))
        ExpandableSubSection(stringResource(Res.string.settings_section_notification), isNotifExpanded, { isNotifExpanded = !isNotifExpanded }) {
            NotificationSettingsContent(notifDistanceStep, notifTime, isAutoLaunchDistEnabled, isAutoLaunchTimeEnabled, isMetric, viewModel)
        }
        ExpandableSubSection(stringResource(Res.string.settings_section_mid_data), isMidDataExpanded, { isMidDataExpanded = !isMidDataExpanded }) {
            MidDataSettingsContent(enabledMidItems, viewModel)
        }
        ExpandableSubSection(stringResource(Res.string.settings_section_lower_data), isLowerDataExpanded, { isLowerDataExpanded = !isLowerDataExpanded }) {
            LowerDataSettingsContent(enabledLowerItems, viewModel)
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

        // 2. マップ表示・操作設定
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_map))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            MapSettingsContent(viewModel)
        }

        // 3. デバイス連携
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
fun NotificationSettingsContent(notifDistanceStep: Float, time: Int, launchDist: Boolean, launchTime: Boolean, isMetric: Boolean, viewModel: SettingsViewModel) {
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
    }
}

@Composable
fun MidDataSettingsContent(enabledMidItems: List<Int>, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = stringResource(Res.string.settings_mid_data_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        val allItems = listOf(
            0 to stringResource(Res.string.settings_mid_item_pace),
            14 to stringResource(Res.string.settings_mid_item_pace_1m),
            15 to stringResource(Res.string.settings_mid_item_pace_2m),
            16 to stringResource(Res.string.settings_mid_item_pace_5m),
            17 to stringResource(Res.string.settings_mid_item_pace_10m),
            7 to stringResource(Res.string.settings_mid_item_avg_pace),
            1 to stringResource(Res.string.settings_mid_item_dist),
            2 to stringResource(Res.string.settings_mid_item_steps),
            3 to stringResource(Res.string.settings_mid_item_alt),
            4 to stringResource(Res.string.settings_mid_item_hr),
            12 to stringResource(Res.string.settings_mid_item_hr_ble),
            13 to stringResource(Res.string.settings_mid_item_hr_watch),
            5 to stringResource(Res.string.settings_mid_item_cal),
            8 to stringResource(Res.string.settings_mid_item_speed),
            9 to stringResource(Res.string.settings_mid_item_clock),
            10 to stringResource(Res.string.settings_mid_item_gain),
            11 to stringResource(Res.string.settings_mid_item_cadence),
            99 to stringResource(Res.string.settings_mid_item_detail)
        )
        val validEnabledItems = enabledMidItems.mapNotNull { typeId ->
            val found = allItems.find { it.first == typeId }
            if (found != null) typeId to found.second else null
        }
        validEnabledItems.forEachIndexed { index, (typeId, name) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { 
                    val newList = enabledMidItems.toMutableList()
                    val actualIndex = newList.indexOf(typeId)
                    if (actualIndex != -1) { newList.removeAt(actualIndex); viewModel.updateMidDataSettings(newList) }
                }) { Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { 
                    val newList = enabledMidItems.toMutableList(); val actualIndex = newList.indexOf(typeId)
                    if (actualIndex > 0) { val t = newList[actualIndex]; newList[actualIndex] = newList[actualIndex-1]; newList[actualIndex-1] = t; viewModel.updateMidDataSettings(newList) }
                }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { 
                    val newList = enabledMidItems.toMutableList(); val actualIndex = newList.indexOf(typeId)
                    if (actualIndex < newList.size - 1) { val t = newList[actualIndex]; newList[actualIndex] = newList[actualIndex+1]; newList[actualIndex+1] = t; viewModel.updateMidDataSettings(newList) }
                }, enabled = index < validEnabledItems.size - 1) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(20.dp)) }
            }
        }
        val disabledItems = allItems.filter { it.first !in enabledMidItems }
        if (disabledItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            disabledItems.forEach { (typeId, name) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { val newList = enabledMidItems.toMutableList(); newList.add(typeId); viewModel.updateMidDataSettings(newList) }) { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun GraphSettingsContent(enabledGraphs: List<Int>, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(8.dp)) {
        val allGraphs = listOf(
            0 to stringResource(Res.string.settings_mid_item_speed), 1 to stringResource(Res.string.settings_mid_item_dist),
            2 to stringResource(Res.string.settings_mid_item_steps), 3 to stringResource(Res.string.settings_mid_item_alt),
            4 to stringResource(Res.string.settings_mid_item_hr), 5 to stringResource(Res.string.settings_mid_item_cal)
        )
        enabledGraphs.forEachIndexed { index, typeId ->
            val name = allGraphs.find { it.first == typeId }?.second ?: "Unknown"
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { val newList = enabledGraphs.toMutableList(); newList.removeAt(index); viewModel.updateGraphSettings(newList) }) { Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { if (index > 0) { val newList = enabledGraphs.toMutableList(); val t = newList[index]; newList[index] = newList[index-1]; newList[index-1] = t; viewModel.updateGraphSettings(newList) } }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { if (index < enabledGraphs.size - 1) { val newList = enabledGraphs.toMutableList(); val t = newList[index]; newList[index] = newList[index+1]; newList[index+1] = t; viewModel.updateGraphSettings(newList) } }, enabled = index < enabledGraphs.size - 1) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(20.dp)) }
            }
        }
        val disabledGraphs = allGraphs.filter { it.first !in enabledGraphs }
        if (disabledGraphs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            disabledGraphs.forEach { (typeId, name) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { val newList = enabledGraphs.toMutableList(); newList.add(typeId); viewModel.updateGraphSettings(newList) }) { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}


@Composable
fun LowerDataSettingsContent(enabledLowerItems: List<Int>, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = stringResource(Res.string.settings_lower_data_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        
        val allItems = listOf(
            // 数値項目
            0 to stringResource(Res.string.settings_mid_item_pace),
            14 to stringResource(Res.string.settings_mid_item_pace_1m),
            15 to stringResource(Res.string.settings_mid_item_pace_2m),
            16 to stringResource(Res.string.settings_mid_item_pace_5m),
            17 to stringResource(Res.string.settings_mid_item_pace_10m),
            7 to stringResource(Res.string.settings_mid_item_avg_pace),
            1 to stringResource(Res.string.settings_mid_item_dist),
            2 to stringResource(Res.string.settings_mid_item_steps),
            3 to stringResource(Res.string.settings_mid_item_alt),
            4 to stringResource(Res.string.settings_mid_item_hr),
            12 to stringResource(Res.string.settings_mid_item_hr_ble),
            13 to stringResource(Res.string.settings_mid_item_hr_watch),
            5 to stringResource(Res.string.settings_mid_item_cal),
            8 to stringResource(Res.string.settings_mid_item_speed),
            9 to stringResource(Res.string.settings_mid_item_clock),
            10 to stringResource(Res.string.settings_mid_item_gain),
            11 to stringResource(Res.string.settings_mid_item_cadence),
            // グラフ項目
            100 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_pace),
            101 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_dist),
            102 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_steps),
            103 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_alt),
            104 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_hr),
            105 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_cal)
        )
        
        val validEnabledItems = enabledLowerItems.mapNotNull { typeId ->
            val found = allItems.find { it.first == typeId }
            if (found != null) typeId to found.second else null
        }
        
        validEnabledItems.forEachIndexed { index, (typeId, name) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { 
                    val newList = enabledLowerItems.toMutableList()
                    val actualIndex = newList.indexOf(typeId)
                    if (actualIndex != -1) { newList.removeAt(actualIndex); viewModel.updateLowerDataSettings(newList) }
                }) { Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { 
                    val newList = enabledLowerItems.toMutableList(); val actualIndex = newList.indexOf(typeId)
                    if (actualIndex > 0) { val t = newList[actualIndex]; newList[actualIndex] = newList[actualIndex-1]; newList[actualIndex-1] = t; viewModel.updateLowerDataSettings(newList) }
                }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { 
                    val newList = enabledLowerItems.toMutableList(); val actualIndex = newList.indexOf(typeId)
                    if (actualIndex != -1 && actualIndex < enabledLowerItems.size - 1) { val t = newList[actualIndex]; newList[actualIndex] = newList[actualIndex+1]; newList[actualIndex+1] = t; viewModel.updateLowerDataSettings(newList) }
                }, enabled = index < validEnabledItems.size - 1) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(20.dp)) }
            }
        }
        
        val disabledItems = allItems.filter { item -> enabledLowerItems.none { it == item.first } }
        if (disabledItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            disabledItems.forEach { (typeId, name) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { val newList = enabledLowerItems.toMutableList(); newList.add(typeId); viewModel.updateLowerDataSettings(newList) }) { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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

    val mapRouteColor by viewModel.mapRouteColor.collectAsState()
    val mapPlannedColor by viewModel.mapPlannedColor.collectAsState()
    val mapLocationColor by viewModel.mapLocationColor.collectAsState()
    val activityZooms by viewModel.activityMapZooms.collectAsState()

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

        // 4. マップ描画色設定ヘッダー
        Text(
            text = stringResource(Res.string.settings_map_colors_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 4-A. 走行実績ルートの色
        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_route_color_title),
            description = stringResource(Res.string.settings_map_route_color_desc),
            currentColor = mapRouteColor,
            onColorSelected = { viewModel.updateMapRouteColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // 4-B. 予定コース（GPX）の色
        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_planned_color_title),
            description = stringResource(Res.string.settings_map_planned_color_desc),
            currentColor = mapPlannedColor,
            onColorSelected = { viewModel.updateMapPlannedColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // 4-C. 現在地マーカーの色
        MapColorSettingRow(
            title = stringResource(Res.string.settings_map_location_color_title),
            description = stringResource(Res.string.settings_map_location_color_desc),
            currentColor = mapLocationColor,
            onColorSelected = { viewModel.updateMapLocationColor(it) }
        )

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 5. ワークアウト別マップ拡大率設定
        Text(
            text = stringResource(Res.string.settings_map_zooms_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(Res.string.settings_map_zooms_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        listOf(
            ActivityType.RUNNING,
            ActivityType.WALKING,
            ActivityType.CYCLING,
            ActivityType.HIKING,
            ActivityType.OTHER
        ).forEach { act ->
            val zoom = activityZooms[act.name] ?: 16
            WorkoutZoomSettingRow(
                activityType = act,
                currentZoom = zoom,
                onZoomSelected = { viewModel.updateActivityMapZoom(act, it) }
            )
        }
        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
        CartoApiKeySettingRow(viewModel)
    }
}

@Composable
private fun getZoomLevelLabel(zoom: Int): String {
    return when (zoom) {
        13 -> stringResource(Res.string.zoom_level_13)
        14 -> stringResource(Res.string.zoom_level_14)
        15 -> stringResource(Res.string.zoom_level_15)
        16 -> stringResource(Res.string.zoom_level_16)
        17 -> stringResource(Res.string.zoom_level_17)
        18 -> stringResource(Res.string.zoom_level_18)
        else -> "Zoom $zoom"
    }
}

@Composable
private fun WorkoutZoomSettingRow(
    activityType: ActivityType,
    currentZoom: Int,
    onZoomSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = activityType.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(getZoomLevelLabel(currentZoom), style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                listOf(13, 14, 15, 16, 17, 18).forEach { z ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = getZoomLevelLabel(z),
                                style = if (z == currentZoom) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                                color = if (z == currentZoom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onZoomSelected(z)
                            expanded = false
                        }
                    )
                }
            }
        }
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

@Composable
private fun CartoApiKeySettingRow(viewModel: SettingsViewModel) {
    val cartoApiKey by viewModel.cartoApiKey.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf(false) }

    Text(
        text = "\u5730\u56f3\u30bf\u30a4\u30eb\u30d7\u30ed\u30d0\u30a4\u30c0\u30fc (CARTO API Key)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = "CARTO\u306e\u7121\u6599API\u30ad\u30fc\u3092\u8a2d\u5b9a\u3059\u308b\u3068\u3001\u9053\u8def\u306e\u8996\u8a8d\u6027\u304c\u5411\u4e0a\u3057\u305f\u300cCARTO Voyager (No Labels)\u300d\u5730\u56f3\u304c\u4f7f\u7528\u3055\u308c\u307e\u3059\u3002\u672a\u5165\u529b\u306e\u5834\u5408\u306fOpenStreetMap\u6a19\u6e96\u5730\u56f3\u3092\u4f7f\u7528\u3057\u307e\u3059\u3002(\u30ad\u30fc\u306f\u7aef\u672b\u30ed\u30fc\u30ab\u30eb\u306b\u5b89\u5168\u306b\u4fdd\u5b58\u3055\u308c\u307e\u3059)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (cartoApiKey.isNotBlank()) "CARTO Voyager (\u6709\u52b9)" else "OpenStreetMap (\u6a19\u6e96)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (cartoApiKey.isNotBlank()) "Key: " + cartoApiKey.take(4) + "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" + cartoApiKey.takeLast(4) else "API\u30ad\u30fc\u672a\u8a2d\u5b9a",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        TextButton(onClick = { showApiKeyDialog = true }) {
            Text(if (cartoApiKey.isNotBlank()) "\u5909\u66f4" else "\u30ad\u30fc\u3092\u8a2d\u5b9a")
        }
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(cartoApiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("CARTO API Key \u8a2d\u5b9a") },
            text = {
                Column {
                    Text(
                        text = "CARTO\u3067\u53d6\u5f97\u3057\u305fAPI Key\u3092\u8cbc\u308a\u4ed8\u3051\u3066\u304f\u3060\u3055\u3044\u3002\u5165\u529b\u3059\u308b\u3068\u81ea\u52d5\u3067CARTO Voyager\u5730\u56f3\u306b\u5207\u308a\u66ff\u308f\u308a\u307e\u3059\u3002(\u7a7a\u306b\u3057\u3066\u4fdd\u5b58\u3059\u308b\u3068OpenStreetMap\u6a19\u6e96\u306b\u623b\u308a\u307e\u3059)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCartoApiKey(tempKey)
                    showApiKeyDialog = false
                }) {
                    Text("\u4fdd\u5b58")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("\u30ad\u30e3\u30f3\u30bb\u30eb")
                }
            }
        )
    }
}
