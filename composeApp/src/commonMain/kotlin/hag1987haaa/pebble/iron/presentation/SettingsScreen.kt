@file:Suppress("SpellCheckingInspection")
package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
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
    val (showPebbleDialog, setShowPebbleDialog) = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Android ターゲットのみ Pebble 権限ダイアログを表示
    hag1987haaa.pebble.iron.presentation.PlatformPebblePermissionDialog(
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
            // 1. プロフィール設定 (体重)
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

            // 2. プライバシーマップ設定
            Text(
                text = stringResource(Res.string.settings_section_privacy),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = if (isPrivacyMapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(Res.string.settings_privacy_map_title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(Res.string.settings_privacy_map_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Switch(
                        checked = isPrivacyMapEnabled,
                        onCheckedChange = { viewModel.updatePrivacyMapModeEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. 外部機器コントロール設定
            Text(
                text = stringResource(Res.string.settings_section_music),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (isMusicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(Res.string.settings_music_title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(Res.string.settings_music_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Switch(
                        checked = isMusicEnabled,
                        onCheckedChange = { viewModel.updateMusicControlEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. 自動化と外部連携設定
            Text(
                text = stringResource(Res.string.settings_section_automation),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = if (isAutoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(Res.string.settings_auto_title), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(Res.string.settings_auto_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Switch(
                            checked = isAutoEnabled,
                            onCheckedChange = { viewModel.updateAutomationEnabled(it) }
                        )
                    }

                    if (isAutoEnabled) {
                        @Suppress("DEPRECATION")
                        val clipboardManager = LocalClipboardManager.current
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Intent Actions (Tap to Copy):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 状態変化
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.ACTION_STATE_CHANGED"))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(Res.string.settings_auto_state_info), style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 各ボタンのトグル
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_50), "ACTION_LONGPRESS_UP", isCmd50Enabled) { viewModel.updateCommand50Enabled(it) }
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_51), "ACTION_LONGPRESS_SELECT", isCmd51Enabled) { viewModel.updateCommand51Enabled(it) }
                            AutomationToggle(stringResource(Res.string.settings_auto_btn_52), "ACTION_LONGPRESS_DOWN", isCmd52Enabled) { viewModel.updateCommand52Enabled(it) }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.settings_auto_footer_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. データ連携 (Health Connect)
            Text(
                text = stringResource(Res.string.settings_section_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(Res.string.settings_hc_title), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.settings_hc_desc),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { actions.requestHealthPermissions() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.settings_hc_button_manage))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. デバイス設定 (Pebble管理アプリ)
            Text(
                text = stringResource(Res.string.settings_section_device),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_pebble_app_title)) },
                    supportingContent = { Text(stringResource(Res.string.settings_pebble_app_desc)) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = { setShowPebbleDialog(true) }) {
                            Text(stringResource(Res.string.settings_button_configure))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.settings_section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_label_version)) },
                supportingContent = { Text("1.0.0 (Iron)") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_label_license)) },
                leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                modifier = Modifier.clickable { onShowLicenses() }
            )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.7f))
        }
        if (checked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
                    .clickable { 
                        clipboardManager.setText(AnnotatedString("hag1987haaa.pebble.iron.$intentAction"))
                    }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = intentAction, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
