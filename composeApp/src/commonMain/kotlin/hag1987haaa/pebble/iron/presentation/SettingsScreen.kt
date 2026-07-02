@file:Suppress("SpellCheckingInspection")
package hag1987haaa.pebble.iron.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import org.jetbrains.compose.resources.stringResource
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: AppActions, onShowLicenses: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val isMusicEnabled by viewModel.isMusicControlEnabled.collectAsState()
    val isLongPressEnabled by viewModel.isLongPressEnabled.collectAsState()
    val upLongPressMode by viewModel.upLongPressMode.collectAsState()
    val selectLongPressMode by viewModel.selectLongPressMode.collectAsState()
    val downLongPressMode by viewModel.downLongPressMode.collectAsState()
    val isAutoEnabled by viewModel.isAutomationEnabled.collectAsState()
    val isCmd50Enabled by viewModel.isCommand50Enabled.collectAsState()
    val isCmd51Enabled by viewModel.isCommand51Enabled.collectAsState()
    val isCmd52Enabled by viewModel.isCommand52Enabled.collectAsState()
    val isPrivacyMapEnabled by viewModel.isPrivacyMapModeEnabled.collectAsState()
    val userWeight by viewModel.userWeight.collectAsState()
    val enabledGraphs by viewModel.enabledGraphTypes.collectAsState()
    val enabledMidItems by viewModel.enabledMidTypes.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val notifDistance by viewModel.notifDistance.collectAsState()
    val notifTime by viewModel.notifTime.collectAsState()
    val isAutoLaunchDistEnabled by viewModel.isAutoLaunchDistEnabled.collectAsState()
    val isAutoLaunchTimeEnabled by viewModel.isAutoLaunchTimeEnabled.collectAsState()
    val hrInterval by viewModel.hrSamplingInterval.collectAsState()
    val isAutoTcx by viewModel.isAutoExportTcxEnabled.collectAsState()
    val isAutoGpx by viewModel.isAutoExportGpxEnabled.collectAsState()
    val tcxUri by viewModel.autoExportTcxUri.collectAsState()
    val gpxUri by viewModel.autoExportGpxUri.collectAsState()

    val (showPebbleDialog, setShowPebbleDialog) = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var isExerciseExpanded by remember { mutableStateOf(false) }
    var isNotifExpanded by remember { mutableStateOf(false) }
    var isMidDataExpanded by remember { mutableStateOf(false) }
    var isGraphsExpanded by remember { mutableStateOf(false) }
    var isAutomationExpanded by remember { mutableStateOf(false) }
    var isHrSettingsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshUris()
    }

    LocalPebblePermissionDialog.current.Show(
        show = showPebbleDialog,
        onDismiss = { setShowPebbleDialog(false) }
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.settings_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 0. ユーザープロファイル
            Text(
                text = stringResource(Res.string.settings_section_profile),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val displayWeight = if (isMetric) userWeight else (userWeight * 2.20462f)
                    val weightUnit = if (isMetric) "kg" else "lb"
                    
                    OutlinedTextField(
                        value = ( (displayWeight * 10).toInt() / 10.0 ).toString(),
                        onValueChange = { 
                            it.toFloatOrNull()?.let { input -> 
                                val weightInKg = if (isMetric) input else (input / 2.20462f)
                                viewModel.updateUserWeight(weightInKg)
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_label_weight)) },
                        supportingText = { Text(stringResource(Res.string.settings_label_weight_desc)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        suffix = { Text(weightUnit) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.settings_unit_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (isMetric) stringResource(Res.string.settings_unit_metric) 
                                else stringResource(Res.string.settings_unit_imperial),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(checked = isMetric, onCheckedChange = { viewModel.updateMetric(it) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 0.5 心拍設定 (独立)
            Text(text = stringResource(Res.string.settings_mid_item_hr), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                onClick = { isHrSettingsExpanded = !isHrSettingsExpanded },
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFE91E63))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(Res.string.settings_label_hr_interval), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(imageVector = if (isHrSettingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            Column(modifier = Modifier.animateContentSize()) {
                if (isHrSettingsExpanded) {
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(Res.string.settings_hr_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            var expanded by remember { mutableStateOf(false) }
                            
                            val stableTitle = stringResource(Res.string.settings_hr_mode_stable)
                            val fastTitle = stringResource(Res.string.settings_hr_mode_fast)
                            val s1 = stringResource(Res.string.settings_hr_interval_1s)
                            val s10 = stringResource(Res.string.settings_hr_interval_10s)
                            val s30 = stringResource(Res.string.settings_hr_interval_30s)
                            val m1 = stringResource(Res.string.settings_hr_interval_1m)
                            val m5 = stringResource(Res.string.settings_hr_interval_5m)
                            val sDefault = stringResource(Res.string.settings_hr_interval_default)

                            val options = listOf(
                                0 to sDefault,
                                // 安定モード (負の値)
                                -10 to "$stableTitle: $s10",
                                -30 to "$stableTitle: $s30",
                                -60 to "$stableTitle: $m1",
                                -300 to "$stableTitle: $m5",
                                // 高速モード (正の値)
                                1 to "$fastTitle: $s1",
                                10 to "$fastTitle: $s10",
                                30 to "$fastTitle: $s30",
                                60 to "$fastTitle: $m1",
                                300 to "$fastTitle: $m5"
                            )
                            
                            val currentLabel = options.find { it.first == hrInterval }?.second ?: sDefault

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(currentLabel); Spacer(modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    options.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = label,
                                                    style = if (value > 0) MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary) 
                                                            else MaterialTheme.typography.bodyMedium
                                                ) 
                                            },
                                            onClick = { viewModel.updateHrSamplingInterval(value); expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. エクササイズ設定 (折り畳み + 階層化)
            Text(
                text = stringResource(Res.string.settings_section_exercise),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                onClick = { isExerciseExpanded = !isExerciseExpanded },
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = stringResource(Res.string.settings_exercise_surface_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(imageVector = if (isExerciseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            Column(modifier = Modifier.animateContentSize()) {
                if (isExerciseExpanded) {
                    // 1-1. ウォッチ通知 (さらに折り畳み)
                    ExpandableSubSection(
                        title = stringResource(Res.string.settings_section_notification),
                        expanded = isNotifExpanded,
                        onToggle = { isNotifExpanded = !isNotifExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = stringResource(Res.string.settings_notif_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_distance_label), style = MaterialTheme.typography.bodyMedium)
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text(if (notifDistance == 0) stringResource(Res.string.settings_notif_off) else "${notifDistance / 1000} km")
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf(0, 1000, 2000, 5000).forEach { dist ->
                                            DropdownMenuItem(text = { Text(if (dist == 0) stringResource(Res.string.settings_notif_off) else "${dist / 1000} km") }, onClick = { viewModel.updateNotifDistance(dist); expanded = false })
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.settings_notif_distance_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Switch(checked = isAutoLaunchDistEnabled, onCheckedChange = { viewModel.updateAutoLaunchDistEnabled(it) }, modifier = Modifier.scale(0.7f))
                            }
                            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_time_label), style = MaterialTheme.typography.bodyMedium)
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text(if (notifTime == 0) stringResource(Res.string.settings_notif_off) else "${notifTime / 60} min")
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf(0, 60, 300, 600, 900).forEach { sec ->
                                            DropdownMenuItem(text = { Text(if (sec == 0) stringResource(Res.string.settings_notif_off) else "${sec / 60} min") }, onClick = { viewModel.updateNotifTime(sec); expanded = false })
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.settings_notif_time_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Switch(checked = isAutoLaunchTimeEnabled, onCheckedChange = { viewModel.updateAutoLaunchTimeEnabled(it) }, modifier = Modifier.scale(0.7f))
                            }
                        }
                    }

                    // 1-2. 中段表示項目 (さらに折り畳み)
                    ExpandableSubSection(
                        title = stringResource(Res.string.settings_section_mid_data),
                        expanded = isMidDataExpanded,
                        onToggle = { isMidDataExpanded = !isMidDataExpanded }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = stringResource(Res.string.settings_mid_data_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                            val allItems = listOf(
                                0 to stringResource(Res.string.settings_mid_item_pace),
                                1 to stringResource(Res.string.settings_mid_item_dist),
                                2 to stringResource(Res.string.settings_mid_item_steps),
                                3 to stringResource(Res.string.settings_mid_item_alt),
                                4 to stringResource(Res.string.settings_mid_item_hr),
                                5 to stringResource(Res.string.settings_mid_item_cal),
                                7 to stringResource(Res.string.settings_mid_item_avg_pace),
                                8 to stringResource(Res.string.settings_mid_item_speed),
                                9 to stringResource(Res.string.settings_mid_item_clock),
                                10 to stringResource(Res.string.settings_mid_item_gain),
                                11 to stringResource(Res.string.settings_mid_item_cadence),
                                99 to stringResource(Res.string.settings_mid_item_detail)
                            )
                            enabledMidItems.forEachIndexed { index, typeId ->
                                val name = allItems.find { it.first == typeId }?.second ?: "Unknown"
                                if (name != "Unknown") {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { val newList = enabledMidItems.toMutableList(); newList.removeAt(index); viewModel.updateMidDataSettings(newList) }) { Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                                        Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        IconButton(onClick = { if (index > 0) { val newList = enabledMidItems.toMutableList(); val t = newList[index]; newList[index] = newList[index-1]; newList[index-1] = t; viewModel.updateMidDataSettings(newList) } }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = { if (index < enabledMidItems.size - 1) { val newList = enabledMidItems.toMutableList(); val t = newList[index]; newList[index] = newList[index+1]; newList[index+1] = t; viewModel.updateMidDataSettings(newList) } }, enabled = index < enabledMidItems.size - 1) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(20.dp)) }
                                    }
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

                    // 1-3. ウォッチグラフ (さらに折り畳み)
                    ExpandableSubSection(
                        title = stringResource(Res.string.settings_section_graphs),
                        expanded = isGraphsExpanded,
                        onToggle = { isGraphsExpanded = !isGraphsExpanded }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = stringResource(Res.string.settings_graphs_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                            val allGraphs = listOf(
                                0 to stringResource(Res.string.settings_mid_item_speed),
                                1 to stringResource(Res.string.settings_mid_item_dist),
                                2 to stringResource(Res.string.settings_mid_item_steps),
                                3 to stringResource(Res.string.settings_mid_item_alt),
                                4 to stringResource(Res.string.settings_mid_item_hr),
                                5 to stringResource(Res.string.settings_mid_item_cal)
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 自動エクスポート
            Text(text = stringResource(Res.string.settings_section_auto_export), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(Res.string.settings_auto_export_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(Res.string.settings_auto_export_tcx), modifier = Modifier.weight(1f))
                        Switch(checked = isAutoTcx, onCheckedChange = { viewModel.updateAutoExportTcxEnabled(it) })
                    }
                    if (isAutoTcx) {
                        ExportFolderSelector(uri = tcxUri, onSelect = { actions.selectAutoExportFolder("tcx") }, onOpen = { actions.openAutoExportFolder("tcx") })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(Res.string.settings_auto_export_gpx), modifier = Modifier.weight(1f))
                        Switch(checked = isAutoGpx, onCheckedChange = { viewModel.updateAutoExportGpxEnabled(it) })
                    }
                    if (isAutoGpx) {
                        ExportFolderSelector(uri = gpxUri, onSelect = { actions.selectAutoExportFolder("gpx") }, onOpen = { actions.openAutoExportFolder("gpx") })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(Res.string.settings_auto_export_strava_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. プライバシー
            Text(text = stringResource(Res.string.settings_section_privacy), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = if (isPrivacyMapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_privacy_map_title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(Res.string.settings_privacy_map_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = isPrivacyMapEnabled, onCheckedChange = { viewModel.updatePrivacyMapModeEnabled(it) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. リモート・自動化連携
            Text(
                text = stringResource(Res.string.settings_section_automation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                onClick = { isAutomationExpanded = !isAutomationExpanded },
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_automation_surface_title),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isAutomationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            Column(modifier = Modifier.animateContentSize()) {
                if (isAutomationExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = stringResource(Res.string.settings_category_touchscreen), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = if (isMusicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(Res.string.settings_touch_title), style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(Res.string.settings_touch_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(checked = isMusicEnabled, onCheckedChange = { viewModel.updateMusicControlEnabled(it) })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(Res.string.settings_touch_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val assistantOptions = listOf(upLongPressMode, selectLongPressMode, downLongPressMode)
                    val hasActiveAssistant = isLongPressEnabled && assistantOptions.any { it == LongPressMode.ASSISTANT }

                    Text(text = stringResource(Res.string.settings_category_longpress), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AdsClick, contentDescription = null, tint = if (isLongPressEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(Res.string.settings_longpress_enable), style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(Res.string.settings_longpress_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(checked = isLongPressEnabled, onCheckedChange = { viewModel.updateLongPressEnabled(it) })
                            }
                            
                            if (isLongPressEnabled) {
                                if (hasActiveAssistant) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = stringResource(Res.string.settings_longpress_assistant_overlay_note),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            TextButton(
                                                onClick = { actions.requestOverlayPermission() },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(stringResource(Res.string.settings_longpress_assistant_overlay_button), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(Res.string.settings_longpress_assistant_note),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                                
                                LongPressButtonSetting(
                                    label = stringResource(Res.string.settings_longpress_up),
                                    currentMode = upLongPressMode,
                                    musicLabel = stringResource(Res.string.settings_longpress_mode_music_prev),
                                    assistantLabel = stringResource(Res.string.settings_longpress_mode_assistant),
                                    intentLabel = stringResource(Res.string.settings_longpress_mode_intent),
                                    noneLabel = stringResource(Res.string.settings_longpress_mode_none),
                                    onModeChanged = { viewModel.updateUpLongPressMode(it) },
                                    intentAction = "ACTION_LONGPRESS_UP",
                                    isIntentEnabled = isCmd50Enabled,
                                    onIntentEnabledChanged = { viewModel.updateCommand50Enabled(it) }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.3.dp)

                                LongPressButtonSetting(
                                    label = stringResource(Res.string.settings_longpress_select),
                                    currentMode = selectLongPressMode,
                                    musicLabel = stringResource(Res.string.settings_longpress_mode_music_play),
                                    assistantLabel = stringResource(Res.string.settings_longpress_mode_assistant),
                                    intentLabel = stringResource(Res.string.settings_longpress_mode_intent),
                                    noneLabel = stringResource(Res.string.settings_longpress_mode_none),
                                    onModeChanged = { viewModel.updateSelectLongPressMode(it) },
                                    intentAction = "ACTION_LONGPRESS_SELECT",
                                    isIntentEnabled = isCmd51Enabled,
                                    onIntentEnabledChanged = { viewModel.updateCommand51Enabled(it) }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.3.dp)

                                LongPressButtonSetting(
                                    label = stringResource(Res.string.settings_longpress_down),
                                    currentMode = downLongPressMode,
                                    musicLabel = stringResource(Res.string.settings_longpress_mode_music_next),
                                    assistantLabel = stringResource(Res.string.settings_longpress_mode_assistant),
                                    intentLabel = stringResource(Res.string.settings_longpress_mode_intent),
                                    noneLabel = stringResource(Res.string.settings_longpress_mode_none),
                                    onModeChanged = { viewModel.updateDownLongPressMode(it) },
                                    intentAction = "ACTION_LONGPRESS_DOWN",
                                    isIntentEnabled = isCmd52Enabled,
                                    onIntentEnabledChanged = { viewModel.updateCommand52Enabled(it) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = stringResource(Res.string.settings_category_external), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = if (isAutoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(Res.string.settings_auto_title), style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(Res.string.settings_auto_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(checked = isAutoEnabled, onCheckedChange = { viewModel.updateAutomationEnabled(it) })
                            }
                            
                            if (isAutoEnabled) {
                                val clipboardManager = LocalClipboardManager.current
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text("Intent Actions (Tap to Copy):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.ACTION_STATE_CHANGED")) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(Res.string.settings_auto_state_info), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = stringResource(Res.string.settings_auto_footer_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. データ連携
            Text(text = stringResource(Res.string.settings_section_data), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(Res.string.settings_hc_title), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(Res.string.settings_hc_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { actions.requestHealthPermissions() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.settings_hc_button_manage))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. デバイス連携
            Text(text = stringResource(Res.string.settings_section_device), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_pebble_app_title)) },
                    supportingContent = { Text(stringResource(Res.string.settings_pebble_app_desc)) },
                    leadingContent = { Icon(Icons.Default.Watch, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { setShowPebbleDialog(true) }) {
                            Text(stringResource(Res.string.settings_button_configure))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 7. アプリについて
            Text(text = stringResource(Res.string.settings_section_about), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_label_version)) },
                supportingContent = { Text("${KmpDependencies.appSettings.appVersion} (Iron)") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_label_license)) },
                leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                modifier = Modifier.clickable { onShowLicenses() }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExpandableSubSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onToggle,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
    }
    if (expanded) {
        content()
    }
}

@Composable
private fun ExportFolderSelector(
    uri: String?,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uri != null) "✓ Selected" else stringResource(Res.string.settings_auto_export_folder_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                if (uri != null) {
                    val folderName = uri.substringAfterLast("%3A").substringAfterLast("/")
                    Text(text = "Folder: $folderName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
            TextButton(onClick = onSelect) {
                Text(stringResource(Res.string.settings_auto_export_folder_select))
            }
        }
        if (uri != null) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.settings_auto_export_folder_open), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LongPressButtonSetting(
    label: String,
    currentMode: LongPressMode,
    musicLabel: String,
    assistantLabel: String,
    intentLabel: String,
    noneLabel: String,
    onModeChanged: (LongPressMode) -> Unit,
    intentAction: String,
    isIntentEnabled: Boolean,
    onIntentEnabledChanged: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onModeChanged(LongPressMode.MUSIC) }) {
            RadioButton(selected = currentMode == LongPressMode.MUSIC, onClick = { onModeChanged(LongPressMode.MUSIC) })
            Text(text = musicLabel, style = MaterialTheme.typography.bodyMedium)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onModeChanged(LongPressMode.ASSISTANT) }) {
            RadioButton(selected = currentMode == LongPressMode.ASSISTANT, onClick = { onModeChanged(LongPressMode.ASSISTANT) })
            Text(text = assistantLabel, style = MaterialTheme.typography.bodyMedium)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onModeChanged(LongPressMode.INTENT) }) {
            RadioButton(selected = currentMode == LongPressMode.INTENT, onClick = { onModeChanged(LongPressMode.INTENT) })
            Text(text = intentLabel, style = MaterialTheme.typography.bodyMedium)
        }

        if (currentMode == LongPressMode.INTENT) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(Res.string.settings_auto_enable_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = isIntentEnabled, onCheckedChange = onIntentEnabledChanged, modifier = Modifier.scale(0.7f))
                    }
                    if (isIntentEnabled) {
                        val clipboardManager = LocalClipboardManager.current
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.$intentAction")) }
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(text = intentAction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onModeChanged(LongPressMode.NONE) }) {
            RadioButton(selected = currentMode == LongPressMode.NONE, onClick = { onModeChanged(LongPressMode.NONE) })
            Text(text = noneLabel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
