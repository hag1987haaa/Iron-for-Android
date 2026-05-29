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
    var runToDelete by remember { mutableStateOf<Long?>(null) }

    if (runToDelete != null) {
        AlertDialog(
            onDismissRequest = { runToDelete = null },
            title = { Text(stringResource(Res.string.history_delete_title)) },
            text = { Text(stringResource(Res.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteRunRecord(runToDelete!!)
                    runToDelete = null
                }) { Text(stringResource(Res.string.history_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { runToDelete = null }) { Text(stringResource(Res.string.history_delete_cancel)) }
            }
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
                    val km = run.distanceMeters / 1000.0
                    val integerPart = km.toInt()
                    val fractionalPart = ((km - integerPart) * 100).toInt()
                    val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
                    val calories = run.calories
                    val caloriesStr = if (calories != null) " - ${calories.toInt()}kcal" else ""
                    val distPrefix = stringResource(Res.string.history_list_item_distance_prefix)
                    Text("$distPrefix$integerPart.${ff}km - ${run.durationSeconds / 60} min (${run.type.getDisplayName()})$caloriesStr")
                },
                trailingContent = {
                    IconButton(onClick = { runToDelete = run.id }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
                modifier = Modifier.clickable { onRunSelected(run.id) }
            )
        }
    }
}
