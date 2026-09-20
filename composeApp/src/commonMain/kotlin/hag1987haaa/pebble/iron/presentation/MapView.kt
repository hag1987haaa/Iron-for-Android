package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import hag1987haaa.pebble.iron.domain.model.LocationPoint

@Composable
fun RouteMapView(
    points: List<LocationPoint>, 
    modifier: Modifier,
    plannedCourses: List<List<LocationPoint>> = emptyList(),
    routeColor: Color = Color(0xFFFF1744),
    plannedCourseColor: Color = Color(0xFFFFD600),
    locationMarkerColor: Color = Color(0xFF00E676),
    isPrivacyMode: Boolean = false,
    isAutoCenter: Boolean = true,
    selectedIndex: Int? = null,
    zoomToTrackKey: Int = 0,
    mapRotation: Float = 0f,
) {
    PlatformMapView(
        points = points,
        modifier = modifier,
        plannedCourses = plannedCourses,
        routeColor = routeColor,
        plannedCourseColor = plannedCourseColor,
        locationMarkerColor = locationMarkerColor,
        isPrivacyMode = isPrivacyMode,
        isAutoCenter = isAutoCenter,
        selectedIndex = selectedIndex,
        zoomToTrackKey = zoomToTrackKey,
        mapRotation = mapRotation
    )
}

@Composable
expect fun PlatformMapView(
    points: List<LocationPoint>, 
    modifier: Modifier,
    plannedCourses: List<List<LocationPoint>>,
    routeColor: Color,
    plannedCourseColor: Color,
    locationMarkerColor: Color,
    isPrivacyMode: Boolean,
    isAutoCenter: Boolean,
    selectedIndex: Int?,
    zoomToTrackKey: Int,
    mapRotation: Float,
)
