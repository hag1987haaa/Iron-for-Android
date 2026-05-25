package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
actual fun PlatformRouteMapView(
    points: List<LocationPoint>,
    modifier: Modifier,
    isPrivacyMode: Boolean
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            setHasTransientState(true)
            // 初回の強制叩き起こし
            onResume()
        }
    }

    // プライバシーモードの切り替えを監視
    LaunchedEffect(isPrivacyMode) {
        if (isPrivacyMode) {
            // 背景を塗りつぶし、地図タイルを非表示にする
            mapView.setBackgroundColor(Color.LTGRAY) // または任意の無地
            mapView.overlayManager.tilesOverlay.isEnabled = false
        } else {
            // 通常の地図表示に戻す
            mapView.setBackgroundColor(Color.TRANSPARENT)
            mapView.overlayManager.tilesOverlay.isEnabled = true
        }
        mapView.invalidate()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // updateが呼ばれた際も念のためonResumeを確認
            if (!view.isLayoutRequested) {
                view.onResume()
            }

            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

                val line = Polyline().apply {
                    setPoints(geoPoints)
                    color = Color.RED
                    width = 8f
                }

                view.overlays.clear()
                view.overlays.add(line)

                view.overlays.add(Marker(view).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                })

                if (geoPoints.size > 1) {
                    view.overlays.add(Marker(view).apply {
                        position = geoPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Finish"
                    })
                }

                var accumulatedDistance = 0.0
                var lastPoint: GeoPoint? = null
                var nextLapDistance = 1000.0

                geoPoints.forEach { point ->
                    if (lastPoint != null) {
                        accumulatedDistance += lastPoint!!.distanceToAsDouble(point)
                        if (accumulatedDistance >= nextLapDistance) {
                            val lapNumber = (nextLapDistance / 1000).toInt()
                            val lapMarker = Marker(view).apply {
                                position = point
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createNumberIcon(view.context, lapNumber)
                                title = "$lapNumber km"
                            }
                            view.overlays.add(lapMarker)
                            nextLapDistance += 1000.0
                        }
                    }
                    lastPoint = point
                }

                try {
                    val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                    view.post {
                        view.zoomToBoundingBox(boundingBox, true, 120)
                    }
                } catch (e: Exception) {}

                view.invalidate()
            } else {
                view.controller.setZoom(4.0)
                view.controller.setCenter(GeoPoint(35.681236, 139.767125))
            }
        }
    )
}

private fun createNumberIcon(context: Context, number: Int): BitmapDrawable {
    val size = 60
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val bgPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
    
    val borderPaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2, borderPaint)
    
    val textPaint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    val textY = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(number.toString(), size / 2f, textY, textPaint)
    
    return BitmapDrawable(context.resources, bitmap)
}
