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
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: AppActions, onShowLicenses: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val isMusicEnabled by viewModel.isMusicControlEnabled.collectAsState()
    val isAutoEnabled by viewModel.isAutomationEnabled.collectAsState()
    val isCmd50Enabled by viewModel.isCommand50Enabled.collectAsState()
    val isCmd51Enabled by viewModel.isCommand51Enabled.collectAsState()
    val isCmd52Enabled by viewModel.isCommand52Enabled.collectAsState()
    val isPrivacyMapEnabled by viewModel.isPrivacyMapModeEnabled.collectAsState()
    val userWeight by viewModel.userWeight.collectAsState()
    val enabledGraphs by viewModel.enabledGraphTypes.collectAsState()
    val notifDistance by viewModel.notifDistance.collectAsState()
    val notifTime by viewModel.notifTime.collectAsState()
    val isAutoLaunchDistEnabled by viewModel.isAutoLaunchDistEnabled.collectAsState()
    val isAutoLaunchTimeEnabled by viewModel.isAutoLaunchTimeEnabled.collectAsState()
    val (showPebbleDialog, setShowPebbleDialog) = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    var isExerciseExpanded by remember { mutableStateOf(false) }

    // Android ターゲットのみ Pebble 権限ダイアログを表示
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
            OutlinedTextField(
                value = userWeight.toString(),
                onValueChange = { 
                    it.toFloatOrNull()?.let { weight -> viewModel.updateUserWeight(weight) }
                },
                label = { Text(stringResource(Res.string.settings_label_weight)) },
                supportingText = { Text(stringResource(Res.string.settings_label_weight_desc)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                suffix = { Text("kg") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. エクササイズ設定 (折り畳み)
            Surface(
                onClick = { isExerciseExpanded = !isExerciseExpanded },
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(Res.string.settings_section_exercise),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isExerciseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            Column(modifier = Modifier.animateContentSize()) {
                if (isExerciseExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // ウォッチ通知
                    Text(text = stringResource(Res.string.settings_section_notification), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp))
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = stringResource(Res.string.settings_notif_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 距離設定
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_distance_label), style = MaterialTheme.typography.bodyMedium)
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text(if (notifDistance == 0) stringResource(Res.string.settings_notif_off) else "${notifDistance / 1000} km")
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf(0, 1000, 2000, 5000).forEach { dist ->
                                            DropdownMenuItem(
                                                text = { Text(if (dist == 0) stringResource(Res.string.settings_notif_off) else "${dist / 1000} km") },
                                                onClick = { viewModel.updateNotifDistance(dist); expanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            // 距離通知時の強制起動
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_distance_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Switch(checked = isAutoLaunchDistEnabled, onCheckedChange = { viewModel.updateAutoLaunchDistEnabled(it) }, modifier = Modifier.scale(0.7f))
                            }
                            
                            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                            
                            // 時間設定
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_time_label), style = MaterialTheme.typography.bodyMedium)
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text(if (notifTime == 0) stringResource(Res.string.settings_notif_off) else "${notifTime / 60} min")
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf(0, 60, 300, 600, 900).forEach { sec ->
                                            DropdownMenuItem(
                                                text = { Text(if (sec == 0) stringResource(Res.string.settings_notif_off) else "${sec / 60} min") },
                                                onClick = { viewModel.updateNotifTime(sec); expanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            // 時間通知時の強制起動
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.settings_notif_time_autolaunch), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Switch(checked = isAutoLaunchTimeEnabled, onCheckedChange = { viewModel.updateAutoLaunchTimeEnabled(it) }, modifier = Modifier.scale(0.7f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ウォッチグラフ
                    Text(text = stringResource(Res.string.settings_section_graphs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp))
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = stringResource(Res.string.settings_graphs_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                            val allGraphs = listOf(
                                0 to stringResource(Res.string.detail_chart_speed),
                                1 to stringResource(Res.string.detail_stat_distance),
                                2 to stringResource(Res.string.detail_stat_steps),
                                3 to stringResource(Res.string.detail_chart_altitude),
                                4 to stringResource(Res.string.detail_chart_heart_rate),
                                5 to stringResource(Res.string.detail_stat_calories)
                            )
                            enabledGraphs.forEachIndexed { index, typeId ->
                                val name = allGraphs.find { it.first == typeId }?.second ?: "Unknown"
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val newList = enabledGraphs.toMutableList(); newList.removeAt(index); viewModel.updateGraphSettings(newList)
                                    }) { Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                                    Text(text = name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = {
                                        if (index > 0) { val newList = enabledGraphs.toMutableList(); val t = newList[index]; newList[index] = newList[index-1]; newList[index-1] = t; viewModel.updateGraphSettings(newList) }
                                    }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                    IconButton(onClick = {
                                        if (index < enabledGraphs.size - 1) { val newList = enabledGraphs.toMutableList(); val t = newList[index]; newList[index] = newList[index+1]; newList[index+1] = t; viewModel.updateGraphSettings(newList) }
                                    }, enabled = index < enabledGraphs.size - 1) { Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                }
                            }
                            val disabledGraphs = allGraphs.filter { it.first !in enabledGraphs }
                            if (disabledGraphs.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                disabledGraphs.forEach { (typeId, name) ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val newList = enabledGraphs.toMutableList(); newList.add(typeId); viewModel.updateGraphSettings(newList)
                                        }) { Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. プライバシー
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

            // 3. リモート・自動化連携トリガー
            Text(text = stringResource(Res.string.settings_section_automation), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column {
                    // 音楽コントロール
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (isMusicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.settings_music_title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(Res.string.settings_music_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = isMusicEnabled, onCheckedChange = { viewModel.updateMusicControlEnabled(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    // インテント
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = if (isAutoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.settings_auto_title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(Res.string.settings_auto_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = isAutoEnabled, onCheckedChange = { viewModel.updateAutomationEnabled(it) })
                    }
                    if (isAutoEnabled) {
                        @Suppress("DEPRECATION")
                        val clipboardManager = LocalClipboardManager.current
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Intent Actions (Tap to Copy):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.ACTION_STATE_CHANGED")) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(Res.string.settings_auto_state_info), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_50), "ACTION_LONGPRESS_UP", isCmd50Enabled) { viewModel.updateCommand50Enabled(it) }
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_51), "ACTION_LONGPRESS_SELECT", isCmd51Enabled) { viewModel.updateCommand51Enabled(it) }
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_52), "ACTION_LONGPRESS_DOWN", isCmd52Enabled) { viewModel.updateCommand52Enabled(it) }
                            Text(text = stringResource(Res.string.settings_auto_footer_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. データ連携
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

            // 5. デバイス連携
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

            // 6. アプリについて
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
private fun AutomationToggle(
    label: String, 
    intentAction: String,
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
        }
        if (checked) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp).clickable { clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.$intentAction")) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = intentAction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
