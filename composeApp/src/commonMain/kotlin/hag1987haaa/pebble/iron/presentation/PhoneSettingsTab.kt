package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import hag1987haaa.pebble.iron.domain.model.BiologicalSex
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsTab(viewModel: SettingsViewModel, actions: AppActions, onShowLicenses: () -> Unit, onShowSimulation: () -> Unit) {
    val userWeight by viewModel.userWeight.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    val isPrivacyMapEnabled by viewModel.isPrivacyMapModeEnabled.collectAsState()
    val isAutoTcx by viewModel.isAutoExportTcxEnabled.collectAsState()
    val isAutoGpx by viewModel.isAutoExportGpxEnabled.collectAsState()
    val tcxUri by viewModel.autoExportTcxUri.collectAsState()
    val gpxUri by viewModel.autoExportGpxUri.collectAsState()
    var showCourseSheet by remember { mutableStateOf(false) }
    val userHeightCm by viewModel.userHeightCm.collectAsState()
    val userBodyFatPct by viewModel.userBodyFatPct.collectAsState()
    val userRestingHr by viewModel.userRestingHr.collectAsState()
    val userBmr by viewModel.userBmr.collectAsState()
    val userSex by viewModel.userSex.collectAsState()
    val userBirthDate by viewModel.userBirthDate.collectAsState()
    var showBirthDatePicker by remember { mutableStateOf(false) }

    if (showBirthDatePicker) {
        BirthDatePickerDialog(
            initial = userBirthDate,
            onDismiss = { showBirthDatePicker = false },
            onConfirm = { date ->
                viewModel.updateUserBirthDate(date)
                showBirthDatePicker = false
            }
        )
    }

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

                // 性別 (心拍ベースのカロリー計算に使用)
                Text(stringResource(Res.string.settings_label_sex))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = userSex == BiologicalSex.MALE,
                        onClick = { viewModel.updateUserSex(BiologicalSex.MALE) },
                        label = { Text(stringResource(Res.string.settings_sex_male)) }
                    )
                    FilterChip(
                        selected = userSex == BiologicalSex.FEMALE,
                        onClick = { viewModel.updateUserSex(BiologicalSex.FEMALE) },
                        label = { Text(stringResource(Res.string.settings_sex_female)) }
                    )
                }
                Spacer(Modifier.height(16.dp))

                // 生年月日 (年齢をカロリー計算に使用)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showBirthDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.settings_label_birth_date))
                        Text(
                            stringResource(Res.string.settings_birth_date_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    val birth = userBirthDate
                    Text(
                        if (birth == null) stringResource(Res.string.settings_birth_date_not_set)
                        else "$birth (${viewModel.userAgeYears() ?: "-"})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))

                // 任意の身体データ (カロリー推定の精度向上)。空欄 = 未設定
                Text(stringResource(Res.string.settings_optional_title))
                Text(
                    stringResource(Res.string.settings_optional_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OptionalNumberField(
                    value = userHeightCm,
                    label = stringResource(Res.string.settings_label_height),
                    description = stringResource(Res.string.settings_height_desc),
                    suffix = if (isMetric) "cm" else "in",
                    validRange = 100f..250f,
                    resetKey = isMetric,
                    toDisplay = { cm -> if (isMetric) cm else cm / 2.54f },
                    fromDisplay = { v -> if (isMetric) v else v * 2.54f },
                    onValueChange = viewModel::updateUserHeight,
                )
                OptionalNumberField(
                    value = userBodyFatPct,
                    label = stringResource(Res.string.settings_label_body_fat),
                    description = stringResource(Res.string.settings_body_fat_desc),
                    suffix = "%",
                    validRange = 3f..60f,
                    decimals = true,
                    onValueChange = viewModel::updateUserBodyFat,
                )
                OptionalNumberField(
                    value = userRestingHr?.toFloat(),
                    label = stringResource(Res.string.settings_label_resting_hr),
                    description = stringResource(Res.string.settings_resting_hr_desc),
                    suffix = "bpm",
                    validRange = 30f..110f,
                    onValueChange = { viewModel.updateUserRestingHr(it?.roundToInt()) },
                )
                OptionalNumberField(
                    value = userBmr,
                    label = stringResource(Res.string.settings_label_bmr),
                    description = stringResource(Res.string.settings_bmr_desc),
                    suffix = "kcal",
                    validRange = 800f..4500f,
                    onValueChange = viewModel::updateUserBmr,
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

        // 2. 自動エクスポート
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

        // 3. プライバシー
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

        // 4. 外部連携・自動化
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_automation))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                AutomationSettingsContent(viewModel, actions)
            }
        }

        // 5. マップ管理
        Spacer(Modifier.height(24.dp))
        SettingsSectionHeader(stringResource(Res.string.settings_section_map_management))
        val messenger = KmpDependencies.trackerEngine.pebbleMessenger
        var cacheSizeBytes by remember { mutableStateOf(messenger?.getMapTileCacheSizeBytes() ?: 0L) }
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // GPX計画コース管理の起動ボタン
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.settings_gpx_course_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.settings_gpx_course_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showCourseSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Route, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.settings_gpx_course_btn))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp)

                // オフライン地図キャッシュ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val sizeMb = cacheSizeBytes.toDouble() / (1024.0 * 1024.0)
                        val sizeStr = if (sizeMb < 0.1) "${cacheSizeBytes / 1024} KB" else "${(sizeMb * 10).toInt() / 10.0} MB"
                        Text(
                            "オフライン地図キャッシュ (${sizeStr})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "GPXコース等の周辺地図タイルを端末内に保存し、電波の届かない圏外やオフライン環境でもPebbleに地図を表示します。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = {
                            messenger?.clearDiskTileCache()
                            cacheSizeBytes = messenger?.getMapTileCacheSizeBytes() ?: 0L
                        }
                    ) {
                        Text("削除")
                    }
                }
            }
        }

        // 7. Data (データ連携)
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

        // Open Source Licenses & Map Attribution Navigation
        Spacer(Modifier.height(24.dp))
        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            onClick = onShowLicenses
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_label_license),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "CARTO, OpenStreetMap, OSS Libraries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
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

    if (showCourseSheet) {
        GpxCoursesSheet(
            actions = actions,
            onDismissRequest = { showCourseSheet = false }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
) {
    // DatePicker は UTC 0時のミリ秒で日付を扱う
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate(1990, 1, 1))
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                onConfirm(millis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date } ?: initial)
            }) { Text(stringResource(Res.string.settings_birth_date_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_birth_date_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

/**
 * Optional numeric profile field: empty = not set (null). Keeps its own text so partial input
 * like "1" or "17." isn't reformatted while typing. [resetKey] re-reads [value] (e.g. unit change).
 * Values outside [validRange] (in stored units, same limits as the calorie model) show an error
 * and are saved as not set.
 */
@Composable
private fun OptionalNumberField(
    value: Float?,
    label: String,
    description: String,
    suffix: String,
    validRange: ClosedFloatingPointRange<Float>,
    decimals: Boolean = false,
    resetKey: Any? = null,
    toDisplay: (Float) -> Float = { it },
    fromDisplay: (Float) -> Float = { it },
    onValueChange: (Float?) -> Unit,
) {
    var text by remember(resetKey) {
        mutableStateOf(
            value?.let { v ->
                val d = toDisplay(v)
                if (decimals) ((d * 10).roundToInt() / 10.0).toString() else d.roundToInt().toString()
            } ?: ""
        )
    }
    val parsed = text.replace(',', '.').toFloatOrNull()?.let(fromDisplay)
    val invalid = text.isNotBlank() && (parsed == null || parsed !in validRange)
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            val v = t.replace(',', '.').toFloatOrNull()?.let(fromDisplay)
            onValueChange(v?.takeIf { it in validRange })
        },
        label = { Text(label) },
        supportingText = { Text(description) },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        suffix = { Text(suffix) }
    )
}
