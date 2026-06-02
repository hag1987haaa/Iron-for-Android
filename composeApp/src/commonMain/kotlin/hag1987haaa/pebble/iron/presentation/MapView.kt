package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hag1987haaa.pebble.iron.domain.model.LocationPoint

@Composable
fun RouteMapView(
    points: List<LocationPoint>, 
    modifier: Modifier,
    isPrivacyMode: Boolean = false,
    isAutoCenter: Boolean = true,
    selectedIndex: Int? = null,
    zoomToTrackKey: Int = 0,
    mapRotation: Float = 0f
) {
    MapViewBackend(points, modifier, isPrivacyMode, isAutoCenter, selectedIndex, zoomToTrackKey, mapRotation)
}

@Composable
expect fun MapViewBackend(
    points: List<LocationPoint>, 
    modifier: Modifier,
    isPrivacyMode: Boolean,
    isAutoCenter: Boolean,
    selectedIndex: Int?,
    zoomToTrackKey: Int,
    mapRotation: Float
)
