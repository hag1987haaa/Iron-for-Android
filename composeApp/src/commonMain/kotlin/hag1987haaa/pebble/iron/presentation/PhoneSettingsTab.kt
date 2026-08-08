package hag1987haaa.pebble.iron.presentation

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
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun PhoneSettingsTab(viewModel: SettingsViewModel, actions: AppActions) {
    val userWeight by viewModel.userWeight.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val isPrivacyMapEnabled by viewModel.isPrivacyMapModeEnabled.collectAsState()
    val isAutoTcx by viewModel.isAutoExportTcxEnabled.collectAsState()
    val isAutoGpx by viewModel.isAutoExportGpxEnabled.collectAsState()
    val tcxUri by viewModel.autoExportTcxUri.collectAsState()
    val gpxUri by viewModel.autoExportGpxUri.collectAsState()
    val preferBleHr by viewModel.preferBleHeartRate.collectAsState()

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        // 1. プロファイル
        SettingsSectionHeader(stringResource(Res.string.settings_section_profile))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val displayWeight = if (isMetric) userWeight else (userWeight * 2.20462f)
                OutlinedTextField(
                    value = ( (displayWeight * 10).toInt() / 10.0 ).toString(),
                    onValueChange = { it.toFloatOrNull()?.let { input -> viewModel.updateUserWeight(if (isMetric) input else (input / 2.20462f)) } },
                    label = { Text(stringResource(Res.string.settings_label_weight)) },
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text(if (isMetric) "kg" else "lb") }
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val unitSystemLabel = if (isMetric) {
                        "${stringResource(Res.string.settings_unit_title)} (Metric / km)"
                    } else {
                        "${stringResource(Res.string.settings_unit_title)} (Imperial / mile)"
                    }
                    Text(unitSystemLabel, modifier = Modifier.weight(1f))
                    Switch(checked = isMetric, onCheckedChange = { viewModel.updateMetric(it) })
                }
            }
        }

        // 2. センサー優先順位
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_sensor_priority))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_label_ble_hr_priority))
                        Text(stringResource(Res.string.settings_desc_ble_hr_priority), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = preferBleHr, onCheckedChange = { viewModel.updatePreferBleHeartRate(it) })
                }
            }
        }

        // 3. 自動エクスポート
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_auto_export))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ExportToggleRow(stringResource(Res.string.settings_auto_export_tcx), isAutoTcx, tcxUri, { viewModel.updateAutoExportTcxEnabled(it) }, { actions.selectAutoExportFolder("tcx") }, { actions.openAutoExportFolder("tcx") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                ExportToggleRow(stringResource(Res.string.settings_auto_export_gpx), isAutoGpx, gpxUri, { viewModel.updateAutoExportGpxEnabled(it) }, { actions.selectAutoExportFolder("gpx") }, { actions.openAutoExportFolder("gpx") })
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(Res.string.settings_auto_export_strava_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        // 4. プライバシー
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_privacy))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_privacy_map_title))
                    Text(stringResource(Res.string.settings_privacy_map_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = isPrivacyMapEnabled, onCheckedChange = { viewModel.updatePrivacyMapModeEnabled(it) })
            }
        }

        // 5. 外部連携・自動化
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_automation))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                AutomationSettingsContent(viewModel, actions)
            }
        }

        // 6. Data
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_data))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.settings_hc_title), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.settings_hc_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { actions.requestHealthPermissions() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Favorite, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.settings_hc_button_manage))
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Iron for Pebble", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            Text(text = "Version ${KmpDependencies.appSettings.appVersion}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AutomationSettingsContent(viewModel: SettingsViewModel, actions: AppActions) {
    val isMusicEnabled by viewModel.isMusicControlEnabled.collectAsState()
    val isLongPressEnabled by viewModel.isLongPressEnabled.collectAsState()
    val upLongPressMode by viewModel.upLongPressMode.collectAsState()
    val selectLongPressMode by viewModel.selectLongPressMode.collectAsState()
    val downLongPressMode by viewModel.downLongPressMode.collectAsState()
    val isAutoEnabled by viewModel.isAutomationEnabled.collectAsState()
    val isCmd50Enabled by viewModel.isCommand50Enabled.collectAsState()
    val isCmd51Enabled by viewModel.isCommand51Enabled.collectAsState()
    val isCmd52Enabled by viewModel.isCommand52Enabled.collectAsState()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_touch_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.settings_touch_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isMusicEnabled, onCheckedChange = { viewModel.updateMusicControlEnabled(it) })
        }
        Text(stringResource(Res.string.settings_touch_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdsClick, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_longpress_enable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.settings_longpress_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isLongPressEnabled, onCheckedChange = { viewModel.updateLongPressEnabled(it) })
        }
        if (isLongPressEnabled) {
            Spacer(Modifier.height(12.dp))
            LongPressButtonSetting(stringResource(Res.string.settings_longpress_up), upLongPressMode, stringResource(Res.string.settings_longpress_mode_music_prev), stringResource(Res.string.settings_longpress_mode_assistant), stringResource(Res.string.settings_longpress_mode_intent), stringResource(Res.string.settings_longpress_mode_none), { viewModel.updateUpLongPressMode(it) }, "ACTION_LONGPRESS_UP", isCmd50Enabled, { viewModel.updateCommand50Enabled(it) })
            LongPressButtonSetting(stringResource(Res.string.settings_longpress_select), selectLongPressMode, stringResource(Res.string.settings_longpress_mode_music_play), stringResource(Res.string.settings_longpress_mode_assistant), stringResource(Res.string.settings_longpress_mode_intent), stringResource(Res.string.settings_longpress_mode_none), { viewModel.updateSelectLongPressMode(it) }, "ACTION_LONGPRESS_SELECT", isCmd51Enabled, { viewModel.updateCommand51Enabled(it) })
            LongPressButtonSetting(stringResource(Res.string.settings_longpress_down), downLongPressMode, stringResource(Res.string.settings_longpress_mode_music_next), stringResource(Res.string.settings_longpress_mode_assistant), stringResource(Res.string.settings_longpress_mode_intent), stringResource(Res.string.settings_longpress_mode_none), { viewModel.updateDownLongPressMode(it) }, "ACTION_LONGPRESS_DOWN", isCmd52Enabled, { viewModel.updateCommand52Enabled(it) })
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.settings_auto_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.settings_auto_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = isAutoEnabled, onCheckedChange = { viewModel.updateAutomationEnabled(it) })
        }
    }
}

@Composable
fun LongPressButtonSetting(label: String, currentMode: LongPressMode, musicLabel: String, assistantLabel: String, intentLabel: String, noneLabel: String, onModeChanged: (LongPressMode) -> Unit, intentAction: String, isIntentEnabled: Boolean, onIntentEnabledChanged: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModeChanged(LongPressMode.MUSIC) }) {
            RadioButton(selected = currentMode == LongPressMode.MUSIC, onClick = { onModeChanged(LongPressMode.MUSIC) }); Text(text = musicLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModeChanged(LongPressMode.ASSISTANT) }) {
            RadioButton(selected = currentMode == LongPressMode.ASSISTANT, onClick = { onModeChanged(LongPressMode.ASSISTANT) }); Text(text = assistantLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModeChanged(LongPressMode.INTENT) }) {
            RadioButton(selected = currentMode == LongPressMode.INTENT, onClick = { onModeChanged(LongPressMode.INTENT) }); Text(text = intentLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (currentMode == LongPressMode.INTENT) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 32.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    Text(stringResource(Res.string.settings_auto_enable_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Switch(checked = isIntentEnabled, onCheckedChange = onIntentEnabledChanged, modifier = Modifier.scale(0.7f))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onModeChanged(LongPressMode.NONE) }) {
            RadioButton(selected = currentMode == LongPressMode.NONE, onClick = { onModeChanged(LongPressMode.NONE) }); Text(text = noneLabel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

