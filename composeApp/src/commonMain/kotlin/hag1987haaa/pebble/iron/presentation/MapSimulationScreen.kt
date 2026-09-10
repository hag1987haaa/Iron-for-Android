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
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapSimulationScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val pebblePlatform by viewModel.pebblePlatform.collectAsState()
    val stats by RunState.currentStats.collectAsState()
    val displayPoints = if (stats.route.isNotEmpty()) {
        stats.route
    } else {
        stats.currentLocation?.let { listOf(it) } ?: emptyList()
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = KmpDependencies.trackerEngine.pebbleMessenger

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
                onSendMap = { w, h ->
                    messenger?.sendMap(displayPoints, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Time 2!") }
                }
            )

            Spacer(Modifier.height(32.dp))
        }
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
    onSendMap: (Int, Int) -> Unit
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(points, mapWidth, mapHeight, isMonochrome) {
        if (messenger != null && points.isNotEmpty()) {
            isLoading = true
            try {
                val rgba = messenger.getMapPreviewRgba(points, mapWidth, mapHeight, isMonochrome)
                if (rgba != null) {
                    val img = ImageBitmap(mapWidth, mapHeight)
                    val buffer = IntArray(mapWidth * mapHeight)
                    rgba.copyInto(buffer)
                    // ImageBitmap へのピクセル流し込み
                    img.readPixels(buffer, 0, 0, mapWidth, mapHeight)
                    // Android上での直接ビットマップ変換
                    previewImage = createAndroidImageBitmap(mapWidth, mapHeight, rgba) ?: img
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

expect fun createAndroidImageBitmap(width: Int, height: Int, rgba: IntArray): ImageBitmap?
