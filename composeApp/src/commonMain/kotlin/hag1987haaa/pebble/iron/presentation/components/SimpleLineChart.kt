package hag1987haaa.pebble.iron.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

@Composable
fun SimpleLineChart(
    title: String,
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.Red,
    minScale: Float? = null,
    maxScale: Float? = null,
    avgOverride: Float? = null
) {
    var rawMin = if (data.isEmpty()) 0f else {
        var min = data[0]
        for (i in 1 until data.size) {
            if (data[i] < min) min = data[i]
        }
        min
    }
    
    var rawMax = if (data.isEmpty()) 0f else {
        var max = data[0]
        for (i in 1 until data.size) {
            if (data[i] > max) max = data[i]
        }
        max
    }

    val effectiveMin = minScale?.let { if (rawMin < it) rawMin else it } ?: rawMin
    val effectiveMax = maxScale?.let { if (rawMax > it) rawMax else it } ?: rawMax
    
    val avgVal = avgOverride ?: if (data.isEmpty()) 0f else {
        var sum = 0f
        for (v in data) sum += v
        sum / data.size
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Min: ${formatVal(rawMin)}", fontSize = 10.sp, color = Color.Gray)
                Text("Avg: ${formatVal(avgVal)}", fontSize = 10.sp, color = Color.Gray)
                Text("Max: ${formatVal(rawMax)}", fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (data.size < 2) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("データ不足", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                // メインのグラフエリア
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val range = (effectiveMax - effectiveMin).coerceAtLeast(1f)

                        // 1. 背景の水平補助線 (4分割)
                        val gridCount = 4
                        for (i in 0..gridCount) {
                            val y = height - (i.toFloat() / gridCount * height)
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(width, y),
                                strokeWidth = 1f
                            )
                        }

                        // 2. グラフ曲線
                        val path = Path().apply {
                            data.forEachIndexed { index, value ->
                                val x = index.toFloat() / (data.size - 1) * width
                                val y = height - ((value - effectiveMin) / range * height)
                                if (index == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                        
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 4f)
                        )
                    }
                }

                // 3. 右端の縦軸数値
                Column(
                    modifier = Modifier.width(32.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = formatVal(effectiveMax), fontSize = 9.sp, color = Color.Gray)
                    Text(text = formatVal((effectiveMax + effectiveMin) / 2), fontSize = 9.sp, color = Color.Gray)
                    Text(text = formatVal(effectiveMin), fontSize = 9.sp, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            // 横軸のガイド
            Row(modifier = Modifier.fillMaxWidth().padding(end = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", fontSize = 10.sp, color = Color.Gray)
                Text("End", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun SimpleBarChart(
    title: String,
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.Red,
    avgOverride: Float? = null
) {
    val maxVal = if (data.isEmpty()) 0f else {
        var max = data[0]
        for (i in 1 until data.size) {
            if (data[i] > max) max = data[i]
        }
        max
    }
    
    val avgVal = avgOverride ?: if (data.isEmpty()) 0f else {
        var sum = 0f
        for (v in data) sum += v
        sum / data.size
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avg: ${formatVal(avgVal)}", fontSize = 10.sp, color = Color.Gray)
                Text("Max: ${formatVal(maxVal)}", fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (data.size < 1) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("データ不足", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val range = maxVal.coerceAtLeast(1f)
                        val barWidth = width / data.size

                        data.forEachIndexed { index, value ->
                            val barHeight = (value / range) * height
                            drawRect(
                                color = color,
                                topLeft = androidx.compose.ui.geometry.Offset(index * barWidth, height - barHeight),
                                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
                            )
                        }
                        
                        drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, height), end = androidx.compose.ui.geometry.Offset(width, height))
                    }
                }
                
                // バーチャート用の簡易的な目盛り
                Column(
                    modifier = Modifier.width(32.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = formatVal(maxVal), fontSize = 9.sp, color = Color.Gray)
                    Text(text = "0", fontSize = 9.sp, color = Color.Gray)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(end = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", fontSize = 10.sp, color = Color.Gray)
                Text("End", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

private fun formatVal(v: Float): String {
    return when {
        v == 0f -> "0"
        v >= 100f -> v.toInt().toString() // 心拍数などは整数のほうが見やすい
        else -> {
            // 小数点2桁まで表示 (速度、ペース、高度、距離など)
            val integerPart = v.toInt()
            val fractionalPart = ((v - integerPart) * 100).toInt().let { if (it < 0) -it else it }.coerceIn(0, 99)
            val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
            "$integerPart.$ff"
        }
    }
}
