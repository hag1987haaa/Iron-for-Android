package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import hag1987haaa.pebble.iron.domain.tracker.RunState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSimulationScreen(onBack: () -> Unit) {
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
            Text(
                "Future Pebble Display Simulation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ResolutionPreview(
                name = "Pebble Classic / Time / Steel",
                width = 144,
                height = 168,
                points = stats.route
            )

            ResolutionPreview(
                name = "Pebble Time 2 (2026 Release)",
                width = 200,
                height = 228,
                points = stats.route
            )

            ResolutionPreview(
                name = "Pebble Time Round",
                width = 180,
                height = 180,
                isRound = true,
                points = stats.route
            )

            ResolutionPreview(
                name = "Pebble Round 2 (Upcoming 2026)",
                width = 260,
                height = 260,
                isRound = true,
                points = stats.route
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
    points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$name (${width}x${height})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val shape = if (isRound) CircleShape else RectangleShape
        
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, shape)
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
