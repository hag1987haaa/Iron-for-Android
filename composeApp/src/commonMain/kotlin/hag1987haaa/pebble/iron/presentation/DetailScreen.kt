package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Straighten
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.presentation.components.SimpleLineChart
import hag1987haaa.pebble.iron.util.getDisplayName
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(runId: Long, actions: AppActions, onBack: () -> Unit) {
    val status by RunState.status.collectAsState()

    // ワークアウト終了後の確認画面(-1)にいる場合、リモート操作（ウォッチ側での保存・破棄）に追従する
    if (runId == -1L) {
        LaunchedEffect(status) {
            if (status == RunStatus.RESULT || status == RunStatus.IDLE) {
                // 保存完了(RESULT)または破棄(IDLE)になったら、自動的に画面を閉じる
                onBack()
            }
        }
    }

    // ワークアウト終了後の確認画面では、システム戻るボタンも無効化
    BackPressHandler(enabled = runId == -1L) {
        // 何もしない（「保存」か「破棄」を強制）
    }

    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(KmpDependencies.runRepository) }
    var runActivity by remember { mutableStateOf<RunActivity?>(null) }
    val scrollState = rememberScrollState()
    var editableName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ActivityType.RUNNING) }
    var isEditingName by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var isPrivacyMapLocal by remember { mutableStateOf(KmpDependencies.appSettings.isPrivacyMapModeEnabled) }
    
    // 横軸の表示モード (false: 時間ベース, true: 距離ベース)
    var isDistanceBased by remember { mutableStateOf(false) }

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
                // 破棄を左に
                Button(
                    onClick = { 
                        actions.discardTracking()
                        onBack() 
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.detail_discard)) }

                // 保存を右に
                Button(
                    onClick = { 
                        // 編集された名前と種別を反映させて保存
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
                        IconButton(onClick = { runActivity?.let { actions.shareGpx(it) } }) {
                            Icon(Icons.Default.Share, contentDescription = "Share GPX")
                        }
                    }
                }
            )
        }
    ) { padding ->
        runActivity?.let { run ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 1. 固定ヘッダーセクション (地図 + ワークアウト基本情報)
                // zIndex(1f) を指定してスクロールコンテンツより前面に配置
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().zIndex(1f)
                ) {
                    Column {
                        // 地図 (少し高さを抑えて情報のバランスを調整)
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            RouteMapView(
                                points = run.route,
                                modifier = Modifier.fillMaxSize(),
                                isPrivacyMode = isPrivacyMapLocal
                            )
                            
                            // 右上にプライバシーマップ切り替え
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text("Privacy", style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(checked = isPrivacyMapLocal, onCheckedChange = { isPrivacyMapLocal = it }, modifier = Modifier.scale(0.6f))
                                }
                            }

                            // 著作権表示
                            val uriHandler = LocalUriHandler.current
                            Text(
                                text = "© OpenStreetMap contributors",
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).graphicsLayer(alpha = 0.6f).clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 基本情報 (名前・種別) - これも固定
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
                                                    // データを再読み込み
                                                    val updatedRun = viewModel.getRunDetails(runId)
                                                    runActivity = updatedRun
                                                    
                                                    // 健康管理アプリ(Health Connect)への同期を自動実行
                                                    updatedRun?.let { runObj ->
                                                        actions.syncWithHealthConnect(runObj) { success ->
                                                            if (success) {
                                                                // 同期成功
                                                            }
                                                        }
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

                // 2. スクロール可能なコンテンツエリア
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 保存・破棄ボタン (確認モード時のみ)
                    actionButtons()

                    // 統計カード
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem(stringResource(Res.string.detail_stat_distance), formatDistance(run.distanceMeters), "KM")
                            StatItem(stringResource(Res.string.detail_stat_time), formatDuration(run.durationSeconds), "")
                            StatItem(stringResource(Res.string.detail_stat_calories), "${run.calories?.toInt() ?: 0}", "kcal")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // グラフセクションヘッダーと軸切り替え
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(Res.string.detail_performance_analysis), style = MaterialTheme.typography.titleMedium)
                        
                        // 時間/距離 切り替えスイッチ
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isDistanceBased) Icons.Default.Straighten else Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDistanceBased) "Distance" else "Time",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Switch(
                                checked = isDistanceBased,
                                onCheckedChange = { isDistanceBased = it },
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // グラフ表示データの加工
                    fun <T> List<T>.downsample(maxPoints: Int): List<T> {
                        if (size <= maxPoints) return this
                        val step = size.toFloat() / (maxPoints - 1)
                        return (0 until maxPoints).map { i -> this[(i * step).toInt().coerceAtMost(size - 1)] }
                    }

                    // 距離ベースの場合は、距離（累積メートル）に対してデータを均等に並べるための加工を行う
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
                    } else {
                        run.route
                    }

                    SimpleLineChart(
                        title = stringResource(Res.string.detail_chart_speed) + if (isDistanceBased) " (km/h vs km)" else " (km/h vs time)",
                        data = displayRoute.map { it.speed?.toFloat()?.let { s -> s * 3.6f } ?: 0f }.downsample(100),
                        color = Color(0xFF2196F3)
                    )

                    SimpleLineChart(
                        title = stringResource(Res.string.detail_chart_heart_rate) + if (isDistanceBased) " (bpm vs km)" else " (bpm vs time)",
                        data = displayRoute.mapNotNull { it.heartRate?.toFloat() }.downsample(100),
                        color = Color(0xFFE91E63)
                    )

                    SimpleLineChart(
                        title = stringResource(Res.string.detail_chart_altitude) + if (isDistanceBased) " (m vs km)" else " (m vs time)",
                        data = displayRoute.map { it.altitude?.toFloat() ?: 0f }.downsample(100),
                        color = Color(0xFF4CAF50)
                    )

                    SimpleLineChart(
                        title = stringResource(Res.string.detail_chart_steps) + if (isDistanceBased) " (steps vs km)" else " (steps vs time)",
                        data = displayRoute.map { it.steps?.toFloat() ?: 0f }.downsample(100),
                        color = Color(0xFFFF9800)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    actionButtons()

                    // Health Connect 同期セクション (最下段)
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
                            val msg = if (it == "Synced") stringResource(Res.string.detail_sync_hc_success) else "Sync Failed"
                            Text(text = msg, style = MaterialTheme.typography.labelSmall, color = if (it == "Synced") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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

private fun formatDistance(meters: Double): String {
    val km = meters / 1000.0
    val integerPart = km.toInt()
    val fractionalPart = ((km - integerPart) * 100).toInt()
    val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
    return "$integerPart.$ff"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val mm = if (m < 10) "0$m" else "$m"
    val ss = if (s < 10) "0$s" else "$s"
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}
