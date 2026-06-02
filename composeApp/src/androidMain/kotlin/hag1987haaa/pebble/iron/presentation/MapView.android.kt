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
actual fun MapViewBackend(
    points: List<LocationPoint>,
    modifier: Modifier,
    isPrivacyMode: Boolean,
    isAutoCenter: Boolean,
    selectedIndex: Int?,
    zoomToTrackKey: Int,
    mapRotation: Float
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setHasTransientState(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            onResume()
        }
    }

    // 地図の回転を同期
    LaunchedEffect(mapRotation) {
        mapView.mapOrientation = -mapRotation // osmdroidは時計回りの負値を期待する場合があるため調整
    }

    // ズーム要求の監視
    LaunchedEffect(zoomToTrackKey) {
        if (points.isNotEmpty()) {
            try {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                mapView.zoomToBoundingBox(boundingBox, true, 120)
            } catch (_: Exception) {}
        }
    }

    // プライバシーモードの切り替え
    LaunchedEffect(isPrivacyMode) {
        if (isPrivacyMode) {
            mapView.setBackgroundColor(Color.LTGRAY)
            mapView.overlayManager.tilesOverlay.isEnabled = false
        } else {
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
            if (!view.isLayoutRequested) { view.onResume() }

            view.overlays.clear()

            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

                // 1. ルート線
                val line = Polyline().apply {
                    setPoints(geoPoints)
                    color = Color.RED
                    width = 8f
                }
                view.overlays.add(line)

                // 2. スタートマーカー
                view.overlays.add(Marker(view).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                })

                if (geoPoints.isNotEmpty()) {
                    val lastIdx = selectedIndex ?: (geoPoints.size - 1)
                    val targetPoint = geoPoints[lastIdx]
                    val bearing = points[lastIdx].bearing?.toFloat() ?: 0f

                    // 初回GPS捕捉時、またはズームが低すぎる場合に自動拡大
                    if (view.zoomLevelDouble < 10.0 && selectedIndex == null) {
                        view.controller.setZoom(16.5)
                        view.controller.setCenter(targetPoint)
                    }

                    view.overlays.add(Marker(view).apply {
                        position = targetPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = if (selectedIndex != null) "Selected" else "Current"
                        
                        if (selectedIndex != null) {
                            icon = createPointIcon(view.context, Color.BLUE)
                        } else {
                            // 現在地アイコン（矢印状）
                            icon = createDirectionIcon(view.context, bearing)
                        }
                    })

                    // オートセンター（追従）
                    if (isAutoCenter && selectedIndex == null) {
                        view.controller.animateTo(targetPoint)
                    } else if (selectedIndex != null) {
                        // シーク中は選択地点を瞬時に表示
                        view.controller.setCenter(targetPoint)
                    }
                }

                // 3. 1kmごとのラップマーカー
                var accumulatedDistance = 0.0
                var lastPoint: GeoPoint? = null
                var nextLapDistance = 1000.0

                geoPoints.forEach { point ->
                    if (lastPoint != null) {
                        accumulatedDistance += lastPoint!!.distanceToAsDouble(point)
                        if (accumulatedDistance >= nextLapDistance) {
                            val lapNumber = (nextLapDistance / 1000).toInt()
                            view.overlays.add(Marker(view).apply {
                                position = point
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createNumberIcon(view.context, lapNumber)
                                title = "$lapNumber km"
                            })
                            nextLapDistance += 1000.0
                        }
                    }
                    lastPoint = point
                }
            }
            view.invalidate()
        }
    )
}

private fun createDirectionIcon(context: Context, bearing: Float): BitmapDrawable {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // 矢印の描画
    val paint = Paint().apply {
        color = Color.parseColor("#2196F3")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    canvas.save()
    canvas.rotate(bearing, size / 2f, size / 2f)
    
    val path = android.graphics.Path().apply {
        moveTo(size / 2f, 10f)
        lineTo(size * 0.8f, size - 10f)
        lineTo(size / 2f, size * 0.7f)
        lineTo(size * 0.2f, size - 10f)
        close()
    }
    canvas.drawPath(path, paint)
    canvas.restore()
    
    return BitmapDrawable(context.resources, bitmap)
}

private fun createPointIcon(context: Context, color: Int): BitmapDrawable {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)
    return BitmapDrawable(context.resources, bitmap)
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
