package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSimulationScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val pebblePlatform by viewModel.pebblePlatform.collectAsState()
    val stats by RunState.currentStats.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
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
                    modifier = Modifier.fillMaxWidth()
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
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Classic") == true
            )

            ResolutionPreview(
                name = "Pebble Time / Time Steel",
                width = 144,
                height = 168,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Time") == true && 
                             pebblePlatform?.contains("Round") == false && 
                             pebblePlatform?.contains("2") == false
            )

            ResolutionPreview(
                name = "Pebble Round 2 (260x260 Model)",
                width = 260,
                height = 260,
                isRound = true,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Round 2") == true
            )

            ResolutionPreview(
                name = "Pebble Time Round",
                width = 180,
                height = 180,
                isRound = true,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Round") == true && pebblePlatform?.contains("Round 2") == false
            )

            ResolutionPreview(
                name = "Pebble 2",
                width = 144,
                height = 168,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Pebble 2") == true
            )

            ResolutionPreview(
                name = "Pebble Time 2 (Prototype)",
                width = 200,
                height = 228,
                points = stats.route,
                isHighlight = pebblePlatform?.contains("Time 2") == true
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ResolutionPreview(
    name: String,
    width: Int,
    height: Int,
    isRound: Boolean = false,
    points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>,
    isHighlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (isHighlight) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$name (${width}x${height})",
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
        ) {
            RouteMapView(
                points = points,
                modifier = Modifier.fillMaxSize(),
                isPrivacyMode = false,
                isAutoCenter = true
            )
            
            // 中央の十字線（位置合わせ用）
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
            Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.2f)).align(Alignment.Center))
        }
    }
}
