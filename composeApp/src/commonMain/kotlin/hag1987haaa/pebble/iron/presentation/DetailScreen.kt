package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.presentation.components.SimpleLineChart
import hag1987haaa.pebble.iron.presentation.components.SimpleBarChart
import hag1987haaa.pebble.iron.util.getDisplayName
import kotlinx.datetime.Clock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(runId: Long, actions: AppActions, onBack: () -> Unit) {
    val status by RunState.status.collectAsState()

    // ワークアウト終了後の確認画面(-1)にいる場合、リモート操作（ウォッチ側での保存・破棄）に追従する
    if (runId == -1L) {
        LaunchedEffect(status) {
            if (status == RunStatus.RESULT || status == RunStatus.IDLE) {
                onBack()
            }
        }
    }

    BackPressHandler(enabled = runId == -1L) {
        // 保存・破棄を強制
    }

    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(KmpDependencies.runRepository) }
    var runActivity by remember { mutableStateOf<RunActivity?>(null) }
    val scrollState = rememberScrollState()
    var editableName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ActivityType.RUNNING) }
    var isEditingName by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var isPrivacyMapLocal by remember { mutableStateOf(KmpDependencies.appSettings.isPrivacyMapModeEnabled) }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var isDistanceBased by remember { mutableStateOf(false) }

    // マップ全画面モードの状態
    var isMapFullScreen by rememberSaveable { mutableStateOf(false) }
    // シークバー（再生位置）のインデックス
    var playbackIndex by remember { mutableStateOf<Int?>(null) }
    // ズームリセット用のキー
    var zoomToTrackKey by remember { mutableStateOf(0) }

    LaunchedEffect(runId) {
        if (runId == -1L) {
            val stats = RunState.currentStats.value
            runActivity = RunActivity(
                name = stats.name,
                startTime = stats.startTime ?: Clock.System.now(),
                type = stats.activityType,
                distanceMeters = stats.totalDistanceMeters,
                durationSeconds = stats.totalSeconds,
                calories = stats.calories,
                steps = stats.steps,
                avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null,
                maxHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.maxOrNull()?.toDouble() else null,
                elevationGain = stats.totalElevationGain,
                route = stats.route
            )
            editableName = stats.name ?: ""
            selectedType = stats.activityType
        } else {
            val details = viewModel.getRunDetails(runId)
            runActivity = details
            editableName = details?.name ?: ""
            selectedType = details?.type ?: ActivityType.RUNNING
        }
    }

    val actionButtons = @Composable {
        if (runId == -1L) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { 
                        actions.discardTracking()
                        onBack() 
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.detail_discard)) }

                Button(
                    onClick = { 
                        val currentStats = RunState.currentStats.value
                        RunState.updateStats(currentStats.copy(
                            name = editableName,
                            activityType = selectedType
                        ))
                        actions.saveTracking()
                        onBack() 
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(Res.string.detail_save)) }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isMapFullScreen) {
                TopAppBar(
                    title = { Text(if (runId == -1L) stringResource(Res.string.detail_title_confirm) else stringResource(Res.string.detail_title_view)) },
                    navigationIcon = {
                        if (runId != -1L) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (runId != -1L) {
                            IconButton(onClick = { showExportDialog = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Export")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text(stringResource(Res.string.export_dialog_title)) },
                text = {
                    Column {
                        Text(stringResource(Res.string.export_dialog_gpx_desc), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(Res.string.export_dialog_tcx_desc), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        runActivity?.let { actions.shareRunData(it, "tcx") }
                        showExportDialog = false 
                    }) { Text(stringResource(Res.string.export_dialog_tcx_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        runActivity?.let { actions.shareRunData(it, "gpx") }
                        showExportDialog = false 
                    }) { Text(stringResource(Res.string.export_dialog_gpx_button)) }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            runActivity?.let { run ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isMapFullScreen) PaddingValues(0.dp) else padding)
                ) {
                    if (!isMapFullScreen) {
                        Surface(
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().zIndex(1f)
                        ) {
                            Column {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                ) {
                                    RouteMapView(
                                        points = run.route,
                                        modifier = Modifier.fillMaxSize(),
                                        isPrivacyMode = isPrivacyMapLocal,
                                        isAutoCenter = false,
                                        mapRotation = 0f
                                    )
                                    
                                    FilledIconButton(
                                        onClick = { isMapFullScreen = true },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(40.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.4f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Expand"
                                        )
                                    }

                                    // 著作権表示 (OSM)
                                    val uriHandler = LocalUriHandler.current
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 4.dp)
                                            .graphicsLayer(alpha = 0.6f)
                                            .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") },
                                        color = Color.Black.copy(alpha = 0.2f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "© OpenStreetMap contributors",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(stringResource(Res.string.detail_label_privacy), style = MaterialTheme.typography.labelSmall)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Switch(checked = isPrivacyMapLocal, onCheckedChange = { isPrivacyMapLocal = it }, modifier = Modifier.scale(0.6f))
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isEditingName || runId == -1L) {
                                        Column {
                                            OutlinedTextField(
                                                value = editableName,
                                                onValueChange = { editableName = it },
                                                label = { Text(stringResource(Res.string.detail_label_name)) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                OutlinedButton(onClick = { typeDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                    val typeLabel = stringResource(Res.string.detail_label_type).replace("%s", selectedType.getDisplayName())
                                                    Text(typeLabel)
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                                DropdownMenu(expanded = typeDropdownExpanded, onDismissRequest = { typeDropdownExpanded = false }) {
                                                    ActivityType.entries.forEach { type ->
                                                        DropdownMenuItem(text = { Text(type.getDisplayName()) }, onClick = { selectedType = type; typeDropdownExpanded = false })
                                                    }
                                                }
                                            }
                                            if (runId != -1L) {
                                                Button(
                                                    onClick = {
                                                        viewModel.viewModelScope.launch {
                                                            viewModel.updateRunName(runId, editableName)
                                                            viewModel.updateActivityType(runId, selectedType, KmpDependencies.appSettings.userWeightKg)
                                                            isEditingName = false
                                                            val updatedRun = viewModel.getRunDetails(runId)
                                                            runActivity = updatedRun
                                                            updatedRun?.let { runObj ->
                                                                actions.syncWithHealthConnect(runObj) { }
                                                                actions.triggerAutoExport(runObj)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                                                ) {
                                                    Text(stringResource(Res.string.detail_save))
                                                }
                                            }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = editableName.ifEmpty { "${selectedType.getDisplayName()} - ${run.startTime.toString().substringBefore("T")}" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { isEditingName = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp)) }
                                        }
                                        Text(
                                            text = "${selectedType.getDisplayName()} - ${run.startTime.toString().substringBefore("T")}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            actionButtons()
                            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                val isMetric = KmpDependencies.appSettings.isMetric
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    StatItem(
                                        stringResource(Res.string.detail_stat_distance), 
                                        formatDistanceLocalized(run.distanceMeters, isMetric), 
                                        stringResource(if (isMetric) Res.string.unit_km else Res.string.unit_mi).uppercase()
                                    )
                                    StatItem(stringResource(Res.string.detail_stat_time), formatDuration(run.durationSeconds), "")
                                    StatItem(
                                        stringResource(Res.string.detail_stat_calories), 
                                        "${run.calories?.toInt() ?: 0}", 
                                        stringResource(Res.string.unit_kcal)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.detail_performance_analysis), style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = if (isDistanceBased) Icons.Default.Straighten else Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isDistanceBased) stringResource(Res.string.detail_stat_distance) else stringResource(Res.string.detail_stat_time), style = MaterialTheme.typography.labelMedium)
                                    Switch(checked = isDistanceBased, onCheckedChange = { isDistanceBased = it }, modifier = Modifier.scale(0.7f))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            fun <T> List<T>.downsample(maxPoints: Int): List<T> {
                                if (size <= maxPoints) return this
                                val step = size.toFloat() / (maxPoints - 1)
                                return (0 until maxPoints).map { i -> this[(i * step).toInt().coerceAtMost(size - 1)] }
                            }
                            val displayRoute = if (isDistanceBased && run.route.size > 2) {
                                val totalDist = run.distanceMeters
                                val step = totalDist / 100.0
                                (0..100).map { i ->
                                    val targetDist = i * step
                                    var accumulated = 0.0
                                    var bestPoint = run.route.first()
                                    for (j in 1 until run.route.size) {
                                        val d = hag1987haaa.pebble.iron.util.LocationUtils.calculateDistance(
                                            run.route[j-1].latitude, run.route[j-1].longitude,
                                            run.route[j].latitude, run.route[j].longitude
                                        )
                                        accumulated += d
                                        bestPoint = run.route[j]
                                        if (accumulated >= targetDist) break
                                    }
                                    bestPoint
                                }
                            } else { run.route }

                            val isMetric = KmpDependencies.appSettings.isMetric
                            SimpleLineChart(
                                title = if (isDistanceBased) {
                                    if (isMetric) stringResource(Res.string.detail_chart_speed_dist_metric)
                                    else stringResource(Res.string.detail_chart_speed_dist_imperial)
                                } else {
                                    if (isMetric) stringResource(Res.string.detail_chart_speed_time_metric)
                                    else stringResource(Res.string.detail_chart_speed_time_imperial)
                                },
                                data = displayRoute.map { it.speed?.toFloat()?.let { s -> if (isMetric) s * 3.6f else s * 2.23694f } ?: 0f }.downsample(100),
                                color = Color(0xFF2196F3)
                            )
                            
                            val calorieIntervalData = displayRoute.mapIndexed { index, point ->
                                if (index == 0) 0f
                                else {
                                    val prevPoint = displayRoute[index - 1]
                                    val duration = (point.timestamp.epochSeconds - prevPoint.timestamp.epochSeconds).coerceAtLeast(1)
                                    val dist = hag1987haaa.pebble.iron.util.LocationUtils.calculateDistance(
                                        prevPoint.latitude, prevPoint.longitude,
                                        point.latitude, point.longitude
                                    )
                                    val pAlt = point.altitude
                                    val prevAlt = prevPoint.altitude
                                    val elevGain = if (pAlt != null && prevAlt != null) {
                                        (pAlt - prevAlt).coerceAtLeast(0.0)
                                    } else 0.0

                                    hag1987haaa.pebble.iron.util.HealthUtils.calculateCalories(
                                        type = run.type,
                                        weightKg = KmpDependencies.appSettings.userWeightKg,
                                        durationSeconds = duration,
                                        distanceMeters = dist,
                                        elevationGainMeters = elevGain,
                                        avgHeartRate = point.heartRate?.toDouble()
                                    ).toFloat()
                                }
                            }.downsample(100)

                            SimpleBarChart(
                                title = if (isDistanceBased) {
                                    if (isMetric) stringResource(Res.string.detail_chart_calories_dist_metric)
                                    else stringResource(Res.string.detail_chart_calories_dist_imperial)
                                } else {
                                    if (isMetric) stringResource(Res.string.detail_chart_calories_time_metric)
                                    else stringResource(Res.string.detail_chart_calories_time_imperial)
                                },
                                data = calorieIntervalData,
                                color = Color(0xFFFF5722)
                            )

                            SimpleLineChart(
                                title = if (isDistanceBased) {
                                    if (isMetric) stringResource(Res.string.detail_chart_altitude_dist_metric)
                                    else stringResource(Res.string.detail_chart_altitude_dist_imperial)
                                } else {
                                    if (isMetric) stringResource(Res.string.detail_chart_altitude_time_metric)
                                    else stringResource(Res.string.detail_chart_altitude_time_imperial)
                                },
                                data = displayRoute.map { it.altitude?.toFloat()?.let { a: Float -> if (isMetric) a else a * 3.28084f } ?: 0f }.downsample(100),
                                color = Color(0xFF4CAF50)
                            )
                            SimpleLineChart(
                                title = if (isDistanceBased) {
                                    if (isMetric) stringResource(Res.string.detail_chart_steps_dist_metric)
                                    else stringResource(Res.string.detail_chart_steps_dist_imperial)
                                } else {
                                    if (isMetric) stringResource(Res.string.detail_chart_steps_time_metric)
                                    else stringResource(Res.string.detail_chart_steps_time_imperial)
                                },
                                data = displayRoute.map { it.steps?.toFloat() ?: 0f }.downsample(100),
                                color = Color(0xFFFF9800)
                            )
                            SimpleLineChart(
                                title = if (isDistanceBased) {
                                    if (isMetric) stringResource(Res.string.detail_chart_hr_dist_metric)
                                    else stringResource(Res.string.detail_chart_hr_dist_imperial)
                                } else {
                                    if (isMetric) stringResource(Res.string.detail_chart_hr_time_metric)
                                    else stringResource(Res.string.detail_chart_hr_time_imperial)
                                },
                                data = displayRoute.mapNotNull { it.heartRate?.toFloat() }.downsample(100),
                                color = Color(0xFFE91E63)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            actionButtons()

                            if (runId != -1L) {
                                Spacer(modifier = Modifier.height(32.dp))
                                var isSyncing by remember { mutableStateOf(false) }
                                var syncMessage by remember { mutableStateOf<String?>(null) }
                                val isSynced = run.healthConnectId != null

                                Button(
                                    onClick = {
                                        isSyncing = true
                                        actions.syncWithHealthConnect(run) { success ->
                                            isSyncing = false
                                            if (success) {
                                                syncMessage = "Synced"
                                                viewModel.viewModelScope.launch { viewModel.getRunDetails(runId)?.let { runActivity = it } }
                                            } else { syncMessage = "Failed" }
                                        }
                                    },
                                    enabled = !isSyncing,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (isSynced) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) else ButtonDefaults.buttonColors()
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = if (isSynced) stringResource(Res.string.detail_sync_hc_update) else stringResource(Res.string.detail_sync_hc))
                                    }
                                }
                                syncMessage?.let {
                                    val msg = if (it == "Synced") stringResource(Res.string.detail_synced) else stringResource(Res.string.detail_sync_failed)
                                    Text(text = msg, style = MaterialTheme.typography.labelSmall, color = if (it == "Synced") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    } else {
                        // --- マップ全画面モード (振り返り用) ---
                        Box(modifier = Modifier.fillMaxSize()) {
                            RouteMapView(
                                points = run.route,
                                modifier = Modifier.fillMaxSize(),
                                isPrivacyMode = isPrivacyMapLocal,
                                isAutoCenter = false,
                                selectedIndex = playbackIndex,
                                zoomToTrackKey = zoomToTrackKey,
                                mapRotation = 0f
                            )

                            // 左上：戻るボタン
                            FilledIconButton(
                                onClick = { 
                                    isMapFullScreen = false
                                    playbackIndex = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .statusBarsPadding(),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Fullscreen")
                            }

                            // 著作権表示 (OSM) 全画面時
                            val uriHandler = LocalUriHandler.current
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .statusBarsPadding()
                                    .graphicsLayer(alpha = 0.6f)
                                    .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") },
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "© OpenStreetMap contributors",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }

                            SmallFloatingActionButton(
                                onClick = { zoomToTrackKey++ },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            ) { Icon(Icons.Default.Refresh, contentDescription = "Fit Track") }

                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 8.dp
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val currentIndex = playbackIndex ?: (run.route.size - 1)
                                    val currentPoint = if (run.route.isNotEmpty()) run.route[currentIndex] else null
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isMetric = KmpDependencies.appSettings.isMetric
                                        val distUnit = stringResource(if (isMetric) Res.string.unit_km else Res.string.unit_mi).uppercase()
                                        Text(
                                            text = formatDistanceLocalized(run.distanceMeters, isMetric) + " " + distUnit,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        currentPoint?.let {
                                            val speedLabel = stringResource(Res.string.settings_mid_item_speed)
                                            val speedValue = if (isMetric) (it.speed?.let { s -> s * 3.6 } ?: 0.0) else (it.speed?.let { s -> s * 2.23694 } ?: 0.0)
                                            val speedUnit = stringResource(if (isMetric) Res.string.unit_kmh else Res.string.unit_mph)
                                            Text(
                                                text = "$speedLabel: ${speedValue.toInt()} $speedUnit",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Slider(
                                        value = (playbackIndex ?: (run.route.size - 1)).toFloat(),
                                        onValueChange = { playbackIndex = it.toInt() },
                                        valueRange = 0f..(run.route.size - 1).coerceAtLeast(0).toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = unit, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatDistanceLocalized(meters: Double, isMetric: Boolean): String {
    val dist = if (isMetric) meters / 1000.0 else meters / 1609.344
    val integerPart = dist.toInt()
    val fractionalPart = ((dist - integerPart.toDouble()) * 100).toInt().coerceIn(0, 99)
    val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
    return "$integerPart.$ff"
}

private fun formatDistance(meters: Double): String {
    return formatDistanceLocalized(meters, true)
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val mm = if (m < 10) "0$m" else "$m"
    val ss = if (s < 10) "0$s" else "$s"
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}
