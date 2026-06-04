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
    color: Color = Color.Red
) {
    // Explicit calculations to avoid unresolved references to extension functions
    val minVal = if (data.isEmpty()) 0f else {
        var min = data[0]
        for (i in 1 until data.size) {
            if (data[i] < min) min = data[i]
        }
        min
    }
    
    val maxVal = if (data.isEmpty()) 0f else {
        var max = data[0]
        for (i in 1 until data.size) {
            if (data[i] > max) max = data[i]
        }
        max
    }
    
    val avgVal = if (data.isEmpty()) 0f else {
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
            
            // 縦軸の値（最小、最大、平均）をヘッダー付近に表示
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Min: ${minVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("Avg: ${avgVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("Max: ${maxVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (data.size < 2) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("データ不足", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val range = (maxVal - minVal).coerceAtLeast(1f)

                    val path = Path().apply {
                        data.forEachIndexed { index, value ->
                            val x = index.toFloat() / (data.size - 1) * width
                            val y = height - ((value - minVal) / range * height)
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 4f)
                    )
                    
                    // 補助線（上下）
                    drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(width, 0f))
                    drawLine(Color.LightGray.copy(alpha = 0.5f), start = androidx.compose.ui.geometry.Offset(0f, height), end = androidx.compose.ui.geometry.Offset(width, height))
                }
            }
            
            // 横軸の時間を明示
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", fontSize = 10.sp, color = Color.Gray)
                Text("Time (Workout Progress)", fontSize = 10.sp, color = Color.Gray)
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
    color: Color = Color.Red
) {
    val maxVal = if (data.isEmpty()) 0f else {
        var max = data[0]
        for (i in 1 until data.size) {
            if (data[i] > max) max = data[i]
        }
        max
    }
    
    val avgVal = if (data.isEmpty()) 0f else {
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
                Text("Avg: ${avgVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
                Text("Max: ${maxVal.toInt()}", fontSize = 10.sp, color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (data.size < 1) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("データ不足", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
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
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", fontSize = 10.sp, color = Color.Gray)
                Text("End", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}
