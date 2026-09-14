package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import hag1987haaa.pebble.iron.util.GpxImporter
import hag1987haaa.pebble.iron.util.GpxCourse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapSimulationScreen(
    actions: AppActions,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val pebblePlatform by viewModel.pebblePlatform.collectAsState()
    val stats by RunState.currentStats.collectAsState()
    val engineStats by KmpDependencies.trackerEngine.statistics.collectAsState()

    var loadedGpxCourse by remember { mutableStateOf<GpxCourse?>(null) }

    val currentLoc = stats.currentLocation ?: engineStats.currentLocation
    val displayPoints = if (stats.route.isNotEmpty()) {
        stats.route
    } else if (loadedGpxCourse != null && loadedGpxCourse!!.points.isNotEmpty()) {
        loadedGpxCourse!!.points
    } else {
        currentLoc?.let { listOf(it) } ?: emptyList()
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = KmpDependencies.trackerEngine.pebbleMessenger

    // メモリ保持のみ: 画面離脱時は予定ルートを自動クリア
    LaunchedEffect(loadedGpxCourse) {
        messenger?.setPlannedCourse(loadedGpxCourse?.points)
    }

    DisposableEffect(Unit) {
        onDispose {
            messenger?.setPlannedCourse(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pebble Resolution Simulation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (pebblePlatform != null) {
                val (nativeMapW, nativeMapH) = getMapSizeForPlatform(pebblePlatform)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (messenger != null) {
                                    messenger.sendMap(displayPoints, nativeMapW, nativeMapH)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Map sent to $pebblePlatform ($nativeMapW x $nativeMapH)!")
                                    }
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Connected Device", style = MaterialTheme.typography.labelSmall)
                            Text(pebblePlatform!!, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("(Long press to send map)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            var isSwipePanMode by remember { mutableStateOf(KmpDependencies.appSettings.isMapSwipePanEnabled) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Map Swipe Action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Touch action on Pebble Time 2 (Emery) & Gabbro during map view",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isSwipePanMode,
                            onCheckedChange = { checked ->
                                isSwipePanMode = checked
                                KmpDependencies.appSettings.isMapSwipePanEnabled = checked
                                KmpDependencies.appSettings.save()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isSwipePanMode) "Current: Map Pan (Scroll Map)" else "Current: Music Control (Prev/Next/Vol)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            var isAutoShowMapOnReady by remember { mutableStateOf(KmpDependencies.appSettings.isAutoShowMapOnReadyEnabled) }
            var autoCloseTimeout by remember { mutableStateOf(KmpDependencies.appSettings.mapAutoCloseTimeoutSeconds) }
            var isTimeoutDropdownExpanded by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-show Map on Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Automatically open map on watch when GPS signal is fixed (Ready)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isAutoShowMapOnReady,
                            onCheckedChange = { checked ->
                                isAutoShowMapOnReady = checked
                                KmpDependencies.appSettings.isAutoShowMapOnReadyEnabled = checked
                                KmpDependencies.appSettings.save()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isAutoShowMapOnReady) "Current: Enabled (Auto-display on Ready)" else "Current: Disabled (Default)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Map Auto-Close Timeout Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Map Auto-Close Timeout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Automatically return to cockpit screen when no map interaction occurs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Box {
                            TextButton(onClick = { isTimeoutDropdownExpanded = true }) {
                                Text(
                                    text = if (autoCloseTimeout == 0) "OFF" else "${autoCloseTimeout}s",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = isTimeoutDropdownExpanded,
                                onDismissRequest = { isTimeoutDropdownExpanded = false }
                            ) {
                                listOf(0, 5, 10, 15, 20, 30).forEach { sec ->
                                    DropdownMenuItem(
                                        text = { Text(if (sec == 0) "OFF (No auto-close)" else "$sec seconds") },
                                        onClick = {
                                            autoCloseTimeout = sec
                                            KmpDependencies.appSettings.mapAutoCloseTimeoutSeconds = sec
                                            KmpDependencies.appSettings.save()
                                            isTimeoutDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (autoCloseTimeout == 0) "Current: Disabled (Manual exit only)" else "Current: Auto-close after ${autoCloseTimeout}s of inactivity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // GPX Course (Planned Route) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPX Course (Planned Route)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Overlay planned course on Pebble map simulation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (loadedGpxCourse == null) {
                        Button(
                            onClick = {
                                actions.pickGpxFile { content ->
                                    val course = GpxImporter.parse(content)
                                    if (course != null && course.points.isNotEmpty()) {
                                        loadedGpxCourse = course
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Loaded: ${course.name.ifBlank { "GPX Route" }} (${course.points.size} pts, ${formatGpxDistance(course.totalDistanceMeters)})")
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Failed to parse GPX file or track is empty.")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select GPX File")
                        }
                    } else {
                        val course = loadedGpxCourse!!
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = course.name.ifBlank { "Imported GPX Track" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Points: ${course.points.size}  •  Distance: ${formatGpxDistance(course.totalDistanceMeters)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    loadedGpxCourse = null
                                    scope.launch { snackbarHostState.showSnackbar("GPX Course cleared") }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }

                            Button(
                                onClick = {
                                    actions.pickGpxFile { content ->
                                        val newCourse = GpxImporter.parse(content)
                                        if (newCourse != null && newCourse.points.isNotEmpty()) {
                                            loadedGpxCourse = newCourse
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Loaded: ${newCourse.name.ifBlank { "GPX Route" }} (${newCourse.points.size} pts)")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Replace")
                            }
                        }
                    }
                }
            }

            Text(
                "Pebble Display Mirroring Simulation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ResolutionPreview(
                name = "Pebble Classic / Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Classic") == true,
                isMonochrome = true,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Classic/Steel!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time / Time Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Time") == true && 
                             pebblePlatform?.contains("Round") == false && 
                             pebblePlatform?.contains("2") == false,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Time!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Round 2 (260x260 Model)",
                width = 260,
                height = 260,
                mapWidth = 260,
                mapHeight = 198,
                isRound = true,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Round 2") == true,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Round 2!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time Round",
                width = 180,
                height = 180,
                mapWidth = 180,
                mapHeight = 136,
                isRound = true,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Round") == true && pebblePlatform?.contains("Round 2") == false,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Time Round!") }
                }
            )

            ResolutionPreview(
                name = "Pebble 2",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Pebble 2") == true,
                isMonochrome = true,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble 2!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time 2 (Prototype)",
                width = 200,
                height = 228,
                mapWidth = 200,
                mapHeight = 176,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Time 2") == true,
                messenger = messenger,
                refreshKey = loadedGpxCourse,
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Time 2!") }
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatGpxDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        val km = meters / 1000.0
        val kmStr = ((km * 10).toInt() / 10.0).toString()
        "$kmStr km"
    } else {
        "${meters.toInt()} m"
    }
}

private fun getMapSizeForPlatform(platform: String?): Pair<Int, Int> {
    return when {
        platform?.contains("Classic") == true || (platform?.contains("Time") == true && !platform.contains("Round") && !platform.contains("2")) || platform?.contains("Pebble 2") == true -> Pair(144, 128)
        platform?.contains("Round 2") == true -> Pair(260, 198)
        platform?.contains("Round") == true -> Pair(180, 136)
        platform?.contains("Time 2") == true -> Pair(200, 176)
        else -> Pair(144, 128)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResolutionPreview(
    name: String,
    width: Int,
    height: Int,
    mapWidth: Int,
    mapHeight: Int,
    isRound: Boolean = false,
    points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>,
    isHighlight: Boolean = false,
    isMonochrome: Boolean = false,
    messenger: PebbleMessenger?,
    refreshKey: Any? = null,
    onSendMap: (Int, Int) -> Unit
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(points, mapWidth, mapHeight, isMonochrome, refreshKey) {
        if (messenger != null && points.isNotEmpty()) {
            isLoading = true
            try {
                val rgba = messenger.getMapPreviewRgba(points, mapWidth, mapHeight, isMonochrome)
                if (rgba != null) {
                    previewImage = platformImageBitmapConverter?.invoke(mapWidth, mapHeight, rgba)
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (isHighlight) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$name (${width}x${height})${if (isMonochrome) " [B/W]" else ""}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        val shape = if (isRound) CircleShape else RectangleShape
        
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
                .border(if (isHighlight) 4.dp else 2.dp, if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .background(Color.Black)
                .clip(shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onSendMap(mapWidth, mapHeight) }
                )
        ) {
            if (previewImage != null) {
                Image(
                    bitmap = previewImage!!,
                    contentDescription = name,
                    modifier = Modifier
                        .size(mapWidth.dp, mapHeight.dp)
                        .align(Alignment.Center),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Waiting for GPS...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            
            // 中央の十字線（位置合わせ用）
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
        }
    }
}

var platformImageBitmapConverter: ((width: Int, height: Int, rgba: IntArray) -> ImageBitmap?)? = null
