package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hag1987haaa.pebble.iron.domain.model.LocationPoint

@Composable
fun RouteMapView(
    points: List<LocationPoint>, 
    modifier: Modifier,
    isPrivacyMode: Boolean = false
) {
    PlatformRouteMapView(points, modifier, isPrivacyMode)
}

@Composable
expect fun PlatformRouteMapView(
    points: List<LocationPoint>, 
    modifier: Modifier,
    isPrivacyMode: Boolean
)
