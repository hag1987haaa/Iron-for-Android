package n1987haaa.trackerkmpforpebble.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import n1987haaa.trackerkmpforpebble.domain.model.LocationPoint

@Composable
actual fun RouteMapView(
    points: List<LocationPoint>,
    modifier: Modifier
) {
    Box(modifier = modifier.background(Color.LightGray)) {
        Text("iOS Map Placeholder", modifier = Modifier.align(Alignment.Center))
    }
}
