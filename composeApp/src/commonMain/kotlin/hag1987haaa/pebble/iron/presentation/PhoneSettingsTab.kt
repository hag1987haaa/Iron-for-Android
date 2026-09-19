package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneSettingsTab(viewModel: SettingsViewModel, actions: AppActions, onShowSimulation: () -> Unit) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { /* Nothing */ },
                    onLongClick = onShowSimulation
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            LongPressButtonSettingDropdown(
                buttonLabel = stringResource(Res.string.settings_longpress_up),
                currentMode = upLongPressMode,
                onModeChanged = { viewModel.updateUpLongPressMode(it) },
                musicLabel = stringResource(Res.string.settings_longpress_mode_music_prev),
                intentAction = "ACTION_LONGPRESS_UP",
                isIntentEnabled = isCmd50Enabled,
                onIntentEnabledChanged = { viewModel.updateCommand50Enabled(it) },
                actions = actions
            )
            LongPressButtonSettingDropdown(
                buttonLabel = stringResource(Res.string.settings_longpress_select),
                currentMode = selectLongPressMode,
                onModeChanged = { viewModel.updateSelectLongPressMode(it) },
                musicLabel = stringResource(Res.string.settings_longpress_mode_music_play),
                intentAction = "ACTION_LONGPRESS_SELECT",
                isIntentEnabled = isCmd51Enabled,
                onIntentEnabledChanged = { viewModel.updateCommand51Enabled(it) },
                actions = actions
            )
            LongPressButtonSettingDropdown(
                buttonLabel = stringResource(Res.string.settings_longpress_down),
                currentMode = downLongPressMode,
                onModeChanged = { viewModel.updateDownLongPressMode(it) },
                musicLabel = stringResource(Res.string.settings_longpress_mode_music_next),
                intentAction = "ACTION_LONGPRESS_DOWN",
                isIntentEnabled = isCmd52Enabled,
                onIntentEnabledChanged = { viewModel.updateCommand52Enabled(it) },
                actions = actions
            )
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
        if (isAutoEnabled) {
            val stateIntentName = "hag1987haaa.pebble.iron.ACTION_STATE_CHANGED"
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 32.dp).fillMaxWidth().clickable { actions.copyToClipboard(stateIntentName) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(text = stateIntentName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Extras: state_name (IDLE, ACTIVE, PAUSED, etc.), state_code (0-6)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun LongPressButtonSettingDropdown(
    buttonLabel: String,
    currentMode: LongPressMode,
    onModeChanged: (LongPressMode) -> Unit,
    musicLabel: String,
    intentAction: String,
    isIntentEnabled: Boolean,
    onIntentEnabledChanged: (Boolean) -> Unit,
    actions: AppActions
) {
    var expanded by remember { mutableStateOf(false) }

    val currentModeLabel = when (currentMode) {
        LongPressMode.MUSIC -> musicLabel
        LongPressMode.MAP -> stringResource(Res.string.settings_longpress_mode_map)
        LongPressMode.ASSISTANT -> stringResource(Res.string.settings_longpress_mode_assistant)
        LongPressMode.INTENT -> stringResource(Res.string.settings_longpress_mode_intent)
        LongPressMode.NONE -> stringResource(Res.string.settings_longpress_mode_none)
    }

    val currentModeIcon = when (currentMode) {
        LongPressMode.MUSIC -> Icons.Default.MusicNote
        LongPressMode.MAP -> Icons.Default.Map
        LongPressMode.ASSISTANT -> Icons.Default.Mic
        LongPressMode.INTENT -> Icons.Default.Terminal
        LongPressMode.NONE -> Icons.Default.Block
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(90.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = currentModeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (currentMode == LongPressMode.NONE) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentModeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(musicLabel) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onModeChanged(LongPressMode.MUSIC)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.settings_longpress_mode_map)) },
                        leadingIcon = { Icon(Icons.Default.Map, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onModeChanged(LongPressMode.MAP)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.settings_longpress_mode_assistant)) },
                        leadingIcon = { Icon(Icons.Default.Mic, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onModeChanged(LongPressMode.ASSISTANT)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.settings_longpress_mode_intent)) },
                        leadingIcon = { Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onModeChanged(LongPressMode.INTENT)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.settings_longpress_mode_none)) },
                        leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onModeChanged(LongPressMode.NONE)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (currentMode == LongPressMode.MAP) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.map_snapshot_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 94.dp)
            )
        }

        if (currentMode == LongPressMode.INTENT) {
            val fullIntentName = "hag1987haaa.pebble.iron.$intentAction"
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.padding(start = 94.dp).fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Text(stringResource(Res.string.settings_auto_enable_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = isIntentEnabled, onCheckedChange = onIntentEnabledChanged, modifier = Modifier.scale(0.7f))
                    }
                }
                if (isIntentEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().clickable { actions.copyToClipboard(fullIntentName) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = fullIntentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

