package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
actual fun PlatformMapView(
    points: List<LocationPoint>,
    modifier: Modifier,
    plannedCourses: List<List<LocationPoint>>,
    routeColor: androidx.compose.ui.graphics.Color,
    plannedCourseColor: androidx.compose.ui.graphics.Color,
    locationMarkerColor: androidx.compose.ui.graphics.Color,
    isPrivacyMode: Boolean,
    isAutoCenter: Boolean,
    selectedIndex: Int?,
    zoomToTrackKey: Int,
    mapRotation: Float,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setHasTransientState(true)
            
            // 地図を水平方向にループさせる（世界の端で白くなるのを防ぐ）
            setHorizontalMapRepetitionEnabled(true)
            setVerticalMapRepetitionEnabled(false)
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)

            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            
            // 日本を中心にしつつ、左右に見切れる領域（未描画エリア）が出ない程度のズーム（4.0）に設定
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(36.2048, 138.2529))

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

            val routeColorInt = routeColor.toArgb()
            val plannedColorInt = plannedCourseColor.toArgb()
            val locationMarkerColorInt = locationMarkerColor.toArgb()

            // 1. 各予定コース（GPX）の独立描画（コース境界で完全に独立したPolyline）
            plannedCourses.forEach { coursePoints ->
                if (coursePoints.size >= 2) {
                    val courseGeoPoints = coursePoints.map { GeoPoint(it.latitude, it.longitude) }
                    view.overlays.add(Polyline().apply {
                        setPoints(courseGeoPoints)
                        color = plannedColorInt
                        width = 7f
                    })
                }
            }

            // 2. 走行実績ルートの描画
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

                val PAUSE_GAP_MS = 10_000L
                val currentSegment = mutableListOf<GeoPoint>()

                points.forEachIndexed { i, pt ->
                    val geoPoint = GeoPoint(pt.latitude, pt.longitude)

                    if (i > 0) {
                        val prevPt = points[i - 1]
                        val timeDiffMs = pt.timestamp.toEpochMilliseconds() - prevPt.timestamp.toEpochMilliseconds()
                        val isPauseGap = pt.isSegmentStart || timeDiffMs >= PAUSE_GAP_MS

                        if (isPauseGap) {
                            if (currentSegment.size >= 2) {
                                view.overlays.add(Polyline().apply {
                                    setPoints(currentSegment.toList())
                                    color = routeColorInt
                                    width = 8f
                                })
                            }

                            if (currentSegment.isNotEmpty()) {
                                val lastGeo = currentSegment.last()
                                val pauseLine = Polyline().apply {
                                    setPoints(listOf(lastGeo, geoPoint))
                                    color = Color.GRAY
                                    width = 6f
                                    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
                                }
                                view.overlays.add(pauseLine)
                            }

                            currentSegment.clear()
                        }
                    }
                    currentSegment.add(geoPoint)
                }

                if (currentSegment.size >= 2) {
                    view.overlays.add(Polyline().apply {
                        setPoints(currentSegment)
                        color = routeColorInt
                        width = 8f
                    })
                }

                // スタートマーカー（2点以上記録されている場合）
                if (points.size >= 2) {
                    view.overlays.add(Marker(view).apply {
                        position = geoPoints.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Start"
                    })
                }

                val lastIdx = selectedIndex ?: (geoPoints.size - 1)
                val targetPoint = geoPoints[lastIdx]

                val bearing = if (selectedIndex != null) {
                    (360f - (points[lastIdx].bearing?.toFloat() ?: 0f)) % 360f
                } else {
                    calculateStableBearing(points, lastIdx)
                }

                if (view.zoomLevelDouble < 10.0 && selectedIndex == null) {
                    view.controller.setZoom(16.5)
                    view.controller.setCenter(targetPoint)
                }

                view.overlays.add(Marker(view).apply {
                    position = targetPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = if (selectedIndex != null) "Selected" else "Current"

                    if (selectedIndex != null) {
                        icon = createPointIcon(view.context, locationMarkerColorInt)
                    } else {
                        icon = createDirectionIcon(view.context, locationMarkerColorInt)
                        rotation = bearing
                        isFlat = true
                    }
                })

                if (isAutoCenter && selectedIndex == null) {
                    view.controller.animateTo(targetPoint)
                } else if (selectedIndex != null) {
                    view.controller.setCenter(targetPoint)
                }

                // 3. 1kmごとのラップマーカー
                var accumulatedDistance = 0.0
                var nextLapDistance = 1000.0

                points.forEachIndexed { i, pt ->
                    if (i > 0) {
                        val prevPt = points[i - 1]
                        val timeDiffMs = pt.timestamp.toEpochMilliseconds() - prevPt.timestamp.toEpochMilliseconds()
                        val isPauseGap = pt.isSegmentStart || timeDiffMs >= PAUSE_GAP_MS

                        if (!isPauseGap) {
                            val prevGeo = GeoPoint(prevPt.latitude, prevPt.longitude)
                            val curGeo = GeoPoint(pt.latitude, pt.longitude)
                            accumulatedDistance += prevGeo.distanceToAsDouble(curGeo)
                            if (accumulatedDistance >= nextLapDistance) {
                                val lapNumber = (nextLapDistance / 1000).toInt()
                                view.overlays.add(Marker(view).apply {
                                    position = curGeo
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = createNumberIcon(view.context, lapNumber)
                                    title = "$lapNumber km"
                                })
                                nextLapDistance += 1000.0
                            }
                        }
                    }
                }
            }
            view.invalidate()
        }
    )
}

private fun createDirectionIcon(context: Context, markerColor: Int): BitmapDrawable {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // 矢印の描画（デフォルトで真上＝北を向く）
    val paint = Paint().apply {
        color = markerColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    val path = android.graphics.Path().apply {
        moveTo(size / 2f, 10f)
        lineTo(size * 0.8f, size - 10f)
        lineTo(size / 2f, size * 0.7f)
        lineTo(size * 0.2f, size - 10f)
        close()
    }
    canvas.drawPath(path, paint)

    // 視認性を高めるため、黒の細いフチ線を追加
    val strokePaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawPath(path, strokePaint)
    
    return BitmapDrawable(context.resources, bitmap)
}

private fun calculateStableBearing(points: List<LocationPoint>, currentIndex: Int): Float {
    if (currentIndex < 0 || points.isEmpty()) return 0f
    val current = points[currentIndex]

    // 1. 速度によるフィルタリング (0.1 m/s ≒ 0.36 km/h 未満は「停止中」とみなして方位を更新しない)
    val speed = current.speed ?: 0.0
    if (speed < 0.1 && currentIndex > 0) {
        // 過去の地点を遡って、最後に移動していた時の方位を探す
        for (i in (currentIndex - 1) downTo 0) {
            val p = points[i]
            if ((p.speed ?: 0.0) >= 0.1) {
                // 移動していた地点の生の方位、またはさらにその時点でのベクトル方位を返す
                return p.bearing?.toFloat() ?: calculateStableBearing(points, i)
            }
        }
    }

    // 2. ベクトル計算 (直近 5m 程度の移動から向きを算出)
    var prevForVector: LocationPoint? = null
    for (i in (currentIndex - 1) downTo 0) {
        val p = points[i]
        val dist = hag1987haaa.pebble.iron.util.LocationUtils.calculateDistance(
            current.latitude, current.longitude,
            p.latitude, p.longitude
        )
        if (dist > 5.0) { // 5m 程度のスパンがあれば方位が安定する
            prevForVector = p
            break
        }
    }

    if (prevForVector != null) {
        val rawBearing = hag1987haaa.pebble.iron.util.LocationUtils.calculateBearing(
            prevForVector.latitude, prevForVector.longitude,
            current.latitude, current.longitude
        ).toFloat()
        // 鏡像修正: 360 - rawBearing で反時計回りを時計回りに反転
        return (360f - rawBearing) % 360f
    }

    // 3. フォールバック: GPS生の方位データ（あれば）
    return current.bearing?.toFloat() ?: 0f
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
