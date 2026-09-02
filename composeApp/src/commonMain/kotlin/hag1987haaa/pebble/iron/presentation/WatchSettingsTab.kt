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
    Column(modifier = Modifier.padding(12.dp)) {
        Text(text = stringResource(Res.string.settings_notif_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(12.dp))
        
        // --- 距離通知 (オートラップ) ---
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

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // --- 時間通知 ---
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
