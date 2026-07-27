@file:Suppress("SpellCheckingInspection")
package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource

enum class SettingsTab {
    PHONE, WATCH, SENSORS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: AppActions, onShowLicenses: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    var currentTab by remember { mutableStateOf(SettingsTab.PHONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_settings)) },
                actions = {
                    IconButton(onClick = onShowLicenses) { Icon(Icons.Default.Terminal, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Tab Switcher (Phone / Watch / Sensors) ---
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                SegmentedButton(
                    selected = currentTab == SettingsTab.PHONE,
                    onClick = { currentTab = SettingsTab.PHONE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text(stringResource(Res.string.settings_tab_phone), fontSize = 11.sp) }
                SegmentedButton(
                    selected = currentTab == SettingsTab.WATCH,
                    onClick = { currentTab = SettingsTab.WATCH },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text(stringResource(Res.string.settings_tab_watch), fontSize = 11.sp) }
                SegmentedButton(
                    selected = currentTab == SettingsTab.SENSORS,
                    onClick = { currentTab = SettingsTab.SENSORS },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text(stringResource(Res.string.settings_tab_sensors), fontSize = 11.sp) }
            }

            // 各タブの内容を表示
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    SettingsTab.PHONE -> PhoneSettingsTab(viewModel, actions)
                    SettingsTab.WATCH -> WatchSettingsTab(viewModel, actions)
                    SettingsTab.SENSORS -> SensorsSettingsTab(viewModel)
                }
            }
        }
    }
}
