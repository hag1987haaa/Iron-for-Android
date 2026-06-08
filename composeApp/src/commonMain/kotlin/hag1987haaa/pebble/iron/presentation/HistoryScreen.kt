package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.util.getDisplayName
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryScreen(actions: AppActions, onRunSelected: (Long) -> Unit) {
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(KmpDependencies.runRepository) }
    val runs by viewModel.runs.collectAsState()
    val runToDelete = remember { mutableStateOf<Long?>(null) }

    if (runToDelete.value != null) {
        AlertDialog(
            onDismissRequest = { runToDelete.value = null },
            title = { Text(stringResource(Res.string.history_delete_title)) },
            text = { Text(stringResource(Res.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runToDelete.value?.let { id ->
                            actions.deleteRunRecord(id)
                        }
                        runToDelete.value = null
                    },
                ) {
                    Text(
                        stringResource(Res.string.history_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { runToDelete.value = null },
                ) {
                    Text(stringResource(Res.string.history_delete_cancel))
                }
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(runs) { run ->
            ListItem(
                headlineContent = { 
                    val runPrefix = stringResource(Res.string.history_list_item_run_prefix)
                    Text(text = run.name ?: "$runPrefix${run.startTime.toString().substringBefore("T")}") 
                },
                supportingContent = { 
                    val isMetric = KmpDependencies.appSettings.isMetric
                    val distance = if (isMetric) run.distanceMeters / 1000.0 else run.distanceMeters / 1609.344
                    val integerPart = distance.toInt()
                    val fractionalPart = ((distance - integerPart.toDouble()) * 100).toInt().coerceIn(0, 99)
                    val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
                    
                    val calories = run.calories
                    val unitKcal = stringResource(Res.string.unit_kcal)
                    val caloriesStr = if (calories != null) " - ${calories.toInt()}$unitKcal" else ""
                    
                    val distPrefix = stringResource(Res.string.history_list_item_distance_prefix)
                    val unitDist = stringResource(if (isMetric) Res.string.unit_km else Res.string.unit_mi)
                    val unitMin = stringResource(Res.string.history_list_item_min)
                    
                    Text("$distPrefix$integerPart.$ff$unitDist - ${run.durationSeconds / 60} $unitMin (${run.type.getDisplayName()})$caloriesStr")
                },
                trailingContent = {
                    IconButton(onClick = { runToDelete.value = run.id }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                modifier = Modifier.clickable { onRunSelected(run.id) },
            )
        }
    }
}
