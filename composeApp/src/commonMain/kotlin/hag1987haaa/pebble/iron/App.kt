package hag1987haaa.pebble.iron

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.presentation.AppActions
import hag1987haaa.pebble.iron.presentation.DetailScreen
import hag1987haaa.pebble.iron.presentation.HistoryScreen
import hag1987haaa.pebble.iron.presentation.SettingsScreen
import hag1987haaa.pebble.iron.presentation.RunViewModel
import hag1987haaa.pebble.iron.presentation.RouteMapView
import hag1987haaa.pebble.iron.presentation.BackPressHandler
import hag1987haaa.pebble.iron.util.getDisplayName
import hag1987haaa.pebble.iron.theme.IronColors
import androidx.compose.ui.platform.LocalUriHandler
import hag1987haaa.pebble.iron.presentation.LicenseScreen
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(actions: AppActions) {
    val navController = rememberNavController()
    val status by RunState.status.collectAsState()
    val stats by RunState.currentStats.collectAsState()

    var lastRedirectedWorkoutStart by remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(status, stats.startTime) {
        val currentBackStackEntry = navController.currentBackStackEntry
        val currentRoute = currentBackStackEntry?.destination?.route
        
        val isOnMainScreen = currentRoute == null || currentRoute.startsWith("main")
        if (status == RunStatus.FINISHED && 
            stats.startTime != null && 
            stats.startTime != lastRedirectedWorkoutStart &&
            isOnMainScreen) {
            
            lastRedirectedWorkoutStart = stats.startTime
            navController.navigate("detail/-1") {
                launchSingleTop = true
            }
        }

        val isConfirming = currentRoute?.startsWith("detail") == true && 
                           currentBackStackEntry?.arguments?.getString("runId") == "-1"
        
        if (isConfirming && (status == RunStatus.RESULT || status == RunStatus.IDLE)) {
            if (status == RunStatus.RESULT) {
                navController.navigate("main?tab=1") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            } else {
                navController.navigate("main?tab=0") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    MaterialTheme {
        NavHost(navController = navController, startDestination = "main") {
            composable("main?tab={tab}") { backStackEntry ->
                val tabIndex = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
                MainScreen(
                    navController = navController,
                    actions = actions,
                    initialTab = tabIndex,
                    onRunSelected = { runId ->
                        navController.navigate("detail/$runId")
                    },
                    onShowLicenses = {
                        navController.navigate("licenses")
                    }
                )
            }
            composable("detail/{runId}") { backStackEntry ->
                val runId = backStackEntry.arguments?.getString("runId")?.toLong() ?: 0L
                DetailScreen(
                    runId = runId,
                    actions = actions,
                    onBack = {
                        if (runId == -1L) {
                            navController.navigate("main?tab=1") {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }
            composable("licenses") {
                LicenseScreen(onBack = { 
                    navController.navigate("main?tab=2") {
                        popUpTo("main") { inclusive = true }
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    actions: AppActions, 
    initialTab: Int = 0, 
    onRunSelected: (Long) -> Unit, 
    onShowLicenses: () -> Unit
) {
    var currentTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val status by RunState.status.collectAsState()

    BackPressHandler(enabled = currentTab != 0) {
        currentTab = 0
        navController.navigate("main?tab=0") {
            popUpTo("main?tab=0") { inclusive = true }
        }
    }

    val onTabSelected: (Int) -> Unit = { index ->
        if (currentTab != index) {
            currentTab = index
            navController.navigate("main?tab=$index") {
                popUpTo("main?tab=0") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    Scaffold(
        topBar = {
            if (currentTab == 1) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.nav_history)) },
                    actions = {
                        IconButton(onClick = { actions.exportData() }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }
                        IconButton(onClick = { actions.importData() }) {
                            Icon(Icons.Default.Add, contentDescription = "Import")
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.Default.Home, stringResource(Res.string.nav_home)) },
                    label = { Text(stringResource(Res.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.List, stringResource(Res.string.nav_history)) },
                    label = { Text(stringResource(Res.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = { Icon(Icons.Default.Settings, stringResource(Res.string.nav_settings)) },
                    label = { Text(stringResource(Res.string.nav_settings)) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> RunScreen(actions)
                1 -> HistoryScreen(actions, onRunSelected = onRunSelected)
                2 -> SettingsScreen(actions = actions, onShowLicenses = onShowLicenses)
            }
        }
    }
}

@Composable
fun RunScreen(actions: AppActions) {
    val viewModel: RunViewModel = viewModel { RunViewModel() }
    val stats by viewModel.statistics.collectAsState()
    val status by RunState.status.collectAsState()
    
    val privacyModeFlow = remember {
        try {
            KmpDependencies.appSettings.isPrivacyMapModeEnabledFlow
        } catch (e: Exception) {
            MutableStateFlow(false)
        }
    }
    val isPrivacyMode by privacyModeFlow.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    
    var isMapFullScreen by rememberSaveable { mutableStateOf(false) }
    var isAutoCenter by rememberSaveable { mutableStateOf(true) }
    var isHeadingUp by rememberSaveable { mutableStateOf(false) }
    var zoomToTrackKey by remember { mutableStateOf(0) }

    val currentBearing = stats.route.lastOrNull()?.bearing?.toFloat() ?: 0f
    val mapRotation = if (isHeadingUp) currentBearing else 0f

    // 全画面表示中の戻るジェスチャー対応
    BackPressHandler(enabled = isMapFullScreen) {
        isMapFullScreen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 地図エリア
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isMapFullScreen) 0.dp else 300.dp)
            ) {
                if (!isMapFullScreen) {
                    RouteMapView(
                        points = stats.route,
                        modifier = Modifier.fillMaxSize(),
                        isPrivacyMode = isPrivacyMode,
                        isAutoCenter = isAutoCenter,
                        zoomToTrackKey = zoomToTrackKey,
                        mapRotation = mapRotation
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
                            contentDescription = "Expand Map"
                        )
                    }

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
                }
            }

            // 2. メイン表示エリア (中央揃えを維持)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 背景層: メインの統計情報
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // ステータスメッセージ
                    if (status != RunStatus.IDLE) {
                        val gpsStatusColor = if (stats.hasGpsFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Text(
                            text = if (stats.hasGpsFix) stringResource(Res.string.run_gps_fixed) else stringResource(Res.string.run_gps_searching), 
                            color = gpsStatusColor, 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.run_ready_to_start),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (status == RunStatus.IDLE) {
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                val typeLabel = stringResource(Res.string.detail_label_type).replace("%s", stats.activityType.getDisplayName())
                                Text(typeLabel)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                ActivityType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.getDisplayName()) },
                                        onClick = {
                                            actions.setActivityType(type)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stats.activityType.getDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = stats.formattedTime, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(Res.string.run_label_time), style = MaterialTheme.typography.labelLarge)

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stats.formattedDistance, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(Res.string.run_label_km), style = MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stats.formattedPace, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(Res.string.run_label_pace), style = MaterialTheme.typography.labelMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stats.currentHeartRate?.toString() ?: "--",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.hrSource == "BLE" && stats.isBleHrActive) Color(IronColors.HEART_RATE_PINK) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(text = "BPM", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 操作ボタン
                    when (status) {
                        RunStatus.IDLE -> Button(onClick = { actions.prepareTracking() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(Res.string.run_btn_start_gps)) }
                        RunStatus.PREPARING -> Button(onClick = { actions.discardTracking() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(Res.string.run_btn_cancel)) }
                        RunStatus.READY -> Button(onClick = { actions.startTracking() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(Res.string.run_btn_start_workout)) }
                        RunStatus.ACTIVE -> Button(onClick = { actions.pauseTracking() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(Res.string.run_btn_pause)) }
                        RunStatus.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            Button(onClick = { actions.resumeTracking() }, modifier = Modifier.weight(1f).fillMaxHeight()) { Text(stringResource(Res.string.run_btn_resume)) }
                            Button(onClick = { actions.finishTracking() }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(Res.string.run_btn_finish)) }
                        }
                        RunStatus.FINISHED -> {
                            Text(stringResource(Res.string.run_status_processing))
                        }
                        RunStatus.RESULT -> {
                            Button(onClick = { actions.resetTracking() }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(Res.string.run_btn_reset)) }
                        }
                    }
                }

                // 前面層: ソースインジケーター (左端に浮かせる)
                // top = 16dp (Column padding) + ~24dp (Status Text) + 16dp (Spacer) = 56dp
                // バランスを見て 64dp に設定
                SourceIndicatorList(
                    stats = stats,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 64.dp, start = 8.dp)
                )
            }
        }

        // --- 3. マップ全画面オーバーレイ ---
        if (isMapFullScreen) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                RouteMapView(
                    points = stats.route,
                    modifier = Modifier.fillMaxSize(),
                    isPrivacyMode = isPrivacyMode,
                    isAutoCenter = isAutoCenter,
                    zoomToTrackKey = zoomToTrackKey,
                    mapRotation = mapRotation
                )

                // A. 上部：統計情報オーバーレイ
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stats.formattedTime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(Res.string.run_label_time), style = MaterialTheme.typography.labelSmall)
                        }
                        VerticalDivider(modifier = Modifier.height(30.dp).padding(horizontal = 16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stats.formattedDistance, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(text = stringResource(Res.string.run_label_km), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // B. 左上：縮小ボタン
                FilledIconButton(
                    onClick = { isMapFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .statusBarsPadding(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Fullscreen")
                }

                // C. 右側：マップコントロール
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { zoomToTrackKey++ },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Fit Track") }

                    SmallFloatingActionButton(
                        onClick = { isAutoCenter = !isAutoCenter },
                        containerColor = if (isAutoCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (isAutoCenter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) { Icon(Icons.Default.MyLocation, contentDescription = "Toggle Auto-Center") }

                    SmallFloatingActionButton(
                        onClick = { isHeadingUp = !isHeadingUp },
                        containerColor = if (isHeadingUp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (isHeadingUp) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                    ) { 
                        Icon(
                            imageVector = if (isHeadingUp) Icons.Default.Navigation else Icons.Default.Explore, 
                            contentDescription = "Orientation Mode",
                            modifier = Modifier.graphicsLayer(rotationZ = if (isHeadingUp) 0f else -currentBearing)
                        ) 
                    }
                }

                // D. 左側：ソースインジケーター (全画面時も表示)
                SourceIndicatorList(
                    stats = stats,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                )

                // E. 下部：操作ボタンオーバーレイ (計測中のみ)
                if (status == RunStatus.ACTIVE || status == RunStatus.PAUSED) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).padding(bottom = 32.dp),
                        color = Color.Transparent
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(64.dp)) {
                            if (status == RunStatus.ACTIVE) {
                                Button(
                                    onClick = { actions.pauseTracking() },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                ) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.run_btn_pause)) }
                            } else {
                                Button(
                                    onClick = { actions.resumeTracking() },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.run_btn_resume)) }
                                
                                Button(
                                    onClick = { actions.finishTracking() },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.run_btn_finish)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceIndicatorList(stats: hag1987haaa.pebble.iron.domain.tracker.RunStatistics, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HR Source
        SourceBadge(
            icon = if (stats.hrSource == "BLE") Icons.Default.Bluetooth else Icons.Default.Watch,
            label = stats.hrSource,
            value = if (stats.currentHeartRate != null && stats.currentHeartRate!! > 0) stats.currentHeartRate.toString() else null,
            isActive = (stats.currentHeartRate ?: 0) > 0,
            activeColor = if (stats.hrSource == "BLE") Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
        )

        // GPS Source
        SourceBadge(
            icon = Icons.Default.GpsFixed,
            label = "GPS",
            value = if (stats.hasGpsFix) "FIX" else null,
            isActive = stats.hasGpsFix,
            activeColor = MaterialTheme.colorScheme.primary
        )

        // Motion (Cadence) Source
        SourceBadge(
            icon = Icons.Default.DirectionsRun,
            label = "Watch",
            value = if (stats.steps > 0) stats.steps.toString() else null,
            isActive = stats.steps > 0,
            activeColor = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SourceBadge(icon: ImageVector, label: String, value: String? = null, isActive: Boolean, activeColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.1f) else Color.Transparent)
            .padding(4.dp)
    ) {
        if (value != null && isActive) {
            Text(
                text = value,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = activeColor,
                lineHeight = 9.sp
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(if (value != null && isActive) 18.dp else 22.dp),
            tint = if (isActive) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    }
}
