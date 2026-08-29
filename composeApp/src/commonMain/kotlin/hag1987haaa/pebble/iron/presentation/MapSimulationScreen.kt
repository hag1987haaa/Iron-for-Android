package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.tracker.RunState
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapSimulationScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val pebblePlatform by viewModel.pebblePlatform.collectAsState()
    val stats by RunState.currentStats.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val messenger = KmpDependencies.trackerEngine.pebbleMessenger

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pebble Resolution Simulator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (pebblePlatform != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                Log.d("MapSimulation", "Card clicked")
                            },
                            onLongClick = {
                                Log.d("MapSimulation", "Card long clicked! Starting map send...")
                                val (w, h) = getMapSizeForPlatform(pebblePlatform)
                                messenger?.sendMap(stats.route, w, h)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Sending Map to $pebblePlatform...")
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
                "Future Pebble Display Simulation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ResolutionPreview(
                name = "Pebble Classic / Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Classic") == true,
                isMonochrome = true,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Classic/Steel!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time / Time Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Time") == true && 
                             pebblePlatform?.contains("Round") == false && 
                             pebblePlatform?.contains("2") == false,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
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
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Round 2") == true,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
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
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Round") == true && pebblePlatform?.contains("Round 2") == false,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble Time Round!") }
                }
            )

            ResolutionPreview(
                name = "Pebble 2",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Pebble 2") == true,
                isMonochrome = true,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
                    scope.launch { snackbarHostState.showSnackbar("Map sent to Pebble 2!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time 2 (Prototype)",
                width = 200,
                height = 228,
                mapWidth = 200,
                mapHeight = 176,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Time 2") == true,
                onSendMap = { w, h ->
                    messenger?.sendMap(stats.route, w, h)
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
    onSendMap: (Int, Int) -> Unit
) {
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
            Box(modifier = Modifier.fillMaxSize()) {
                RouteMapView(
                    points = points,
                    modifier = Modifier.fillMaxSize(),
                    isPrivacyMode = false,
                    isAutoCenter = true
                )
                
                // モノクロシミュレーション（半透明のフィルターを被せるなどでも可能だが、
                // 本来はビットマップ処理が必要。ここでは簡易的にオーバーレイを追加）
                if (isMonochrome) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
                }
            }
            
            // 中央の十字線（位置合わせ用）
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
        }
    }
}
