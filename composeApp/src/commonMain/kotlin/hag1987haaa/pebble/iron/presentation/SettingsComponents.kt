package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun ExpandableSubSection(
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
    if (expanded) content()
}

@Composable
fun ExportToggleRow(
    label: String,
    enabled: Boolean,
    uri: String?,
    onToggle: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            ExportFolderSelector(uri = uri, onSelect = onSelect, onOpen = onOpen)
        }
    }
}

@Composable
fun ExportFolderSelector(uri: String?, onSelect: () -> Unit, onOpen: () -> Unit) {
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
            TextButton(onClick = onSelect) { Text(stringResource(Res.string.settings_auto_export_folder_select)) }
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
fun HrIntervalSelector(current: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val stableTitle = stringResource(Res.string.settings_hr_mode_stable)
    val fastTitle = stringResource(Res.string.settings_hr_mode_fast)
    val options = listOf(
        0 to stringResource(Res.string.settings_hr_interval_default),
        -10 to "$stableTitle: 10s", -30 to "$stableTitle: 30s", -60 to "$stableTitle: 1m",
        1 to "$fastTitle: 1s", 10 to "$fastTitle: 10s", 60 to "$fastTitle: 1m"
    )
    val label = options.find { it.first == current }?.second ?: "Default"
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelected(v); expanded = false }) }
        }
    }
}
