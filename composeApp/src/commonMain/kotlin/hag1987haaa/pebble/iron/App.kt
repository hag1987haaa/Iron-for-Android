package hag1987haaa.pebble.iron

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.presentation.AppActions
import hag1987haaa.pebble.iron.presentation.DetailScreen
import hag1987haaa.pebble.iron.presentation.HistoryScreen
import hag1987haaa.pebble.iron.presentation.SettingsScreen
import hag1987haaa.pebble.iron.presentation.RunViewModel
import hag1987haaa.pebble.iron.presentation.RouteMapView
import hag1987haaa.pebble.iron.presentation.BackPressHandler
import hag1987haaa.pebble.iron.util.FormatUtils
import hag1987haaa.pebble.iron.util.getDisplayName
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

    // [保存・破棄の儀式] 強化: FINISHED状態になったら自動的に詳細(確認)画面へ
    // 既に遷移済みのワークアウトを再度表示しないよう、開始時間で制御
    var lastRedirectedWorkoutStart by remember { mutableStateOf<Instant?>(null) }

    LaunchedEffect(status, stats.startTime) {
        val currentBackStackEntry = navController.currentBackStackEntry
        val currentRoute = currentBackStackEntry?.destination?.route
        
        // 1. FINISHEDステータスになったら詳細画面へ飛ばす（既存ロジック）
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

        // 2. 詳細画面（-1L）表示中にステータスが変わったら、強制的に閉じる（連動ロジック）
        val isConfirming = currentRoute?.startsWith("detail") == true && 
                           currentBackStackEntry?.arguments?.getString("runId") == "-1"
        
        if (isConfirming && (status == RunStatus.RESULT || status == RunStatus.IDLE)) {
            // 保存・破棄が完了したため、画面を閉じる
            if (status == RunStatus.RESULT) {
                // 保存された場合は履歴画面へ
                navController.navigate("main?tab=1") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            } else {
                // 破棄された場合はホームへ
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
                            // 保存・破棄後は、バックスタック（確認画面や以前のホーム）を根こそぎクリアして、
                            // まっさらな状態で履歴画面(tab=1)を起動する
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
                LicenseScreen(onBack = { navController.popBackStack() })
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

    // 戻るジェスチャーの制御 (アプリ終了防止・タブ戻り)
    BackPressHandler(enabled = currentTab != 0) {
        currentTab = 0
        navController.navigate("main?tab=0") {
            popUpTo("main?tab=0") { inclusive = true }
        }
    }

    // タブ切り替え時のナビゲーション同期
    val onTabSelected: (Int) -> Unit = { index ->
        if (currentTab != index) {
            currentTab = index
            navController.navigate("main?tab=$index") {
                // 同じ画面の重複スタックを防止
                popUpTo("main?tab=0") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 外部（ナビゲーション引数など）からのタブ指定変更を監視して反映
    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    // 以前あった自動リセット(RESULT -> IDLE)は、ユーザーのリクエスト（次のGPSサーチまでデータを保持したい）に基づき削除しました。

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
    
    // 設定変更をリアルタイムに反映
    val privacyModeFlow = remember {
        try {
            KmpDependencies.appSettings.isPrivacyMapModeEnabledFlow
        } catch (e: Exception) {
            MutableStateFlow(false)
        }
    }
    val isPrivacyMode by privacyModeFlow.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    
    // マップ全画面モードの状態
    var isMapFullScreen by rememberSaveable { mutableStateOf(false) }
    // マップの設定状態
    var isAutoCenter by rememberSaveable { mutableStateOf(true) }
    var isHeadingUp by rememberSaveable { mutableStateOf(false) }
    var zoomToTrackKey by remember { mutableStateOf(0) }

    val currentBearing = stats.route.lastOrNull()?.bearing?.toFloat() ?: 0f
    val mapRotation = if (isHeadingUp) currentBearing else 0f

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
                    
                    // 拡大アイコンを右上に表示
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
                }
            }

            // 2. メッセージと統計情報を下部のカラムに配置
            Column(
                modifier = Modifier
                    .weight(1f)
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
                    // ワークアウト種別選択 (Dropdown)
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
                    // 全景表示ボタン
                    SmallFloatingActionButton(
                        onClick = { zoomToTrackKey++ },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Fit Track") }

                    // オートセンター切り替え
                    SmallFloatingActionButton(
                        onClick = { isAutoCenter = !isAutoCenter },
                        containerColor = if (isAutoCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (isAutoCenter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) { Icon(Icons.Default.MyLocation, contentDescription = "Toggle Auto-Center") }

                    // 方向制御（ノースアップ / ノーズアップ）
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

                // D. 下部：操作ボタンオーバーレイ (計測中のみ)
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
