package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import hag1987haaa.pebble.iron.domain.model.LocationPoint

@Composable
actual fun PlatformRouteMapView(
    points: List<LocationPoint>,
    modifier: Modifier,
    isPrivacyMode: Boolean,
    isAutoCenter: Boolean,
    selectedIndex: Int?,
    zoomToTrackKey: Int,
    mapRotation: Float
) {
    Box(modifier = modifier.background(Color.LightGray)) {
        Text("Map Placeholder", modifier = Modifier.align(Alignment.Center))
    }
}
