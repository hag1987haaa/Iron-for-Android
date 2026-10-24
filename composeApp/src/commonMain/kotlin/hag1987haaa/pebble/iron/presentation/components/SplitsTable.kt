package hag1987haaa.pebble.iron.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.detail_splits_distance
import hag1987haaa.pebble.iron.detail_splits_fastest
import hag1987haaa.pebble.iron.detail_splits_pace
import hag1987haaa.pebble.iron.detail_splits_time
import hag1987haaa.pebble.iron.detail_splits_title
import hag1987haaa.pebble.iron.unit_km
import hag1987haaa.pebble.iron.unit_mi
import hag1987haaa.pebble.iron.util.FormatUtils
import hag1987haaa.pebble.iron.util.SplitItem
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SplitsTable(
    splits: List<SplitItem>,
    isMetric: Boolean,
    modifier: Modifier = Modifier
) {
    if (splits.isEmpty()) return

    val unitLabel = stringResource(if (isMetric) Res.string.unit_km else Res.string.unit_mi)
    val fastestPace = splits.minOfOrNull { it.paceSeconds } ?: 0.0

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ヘッダー行：タイトル
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.detail_splits_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // カラムヘッダー（距離、ペース、タイム、バー）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.detail_splits_distance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(60.dp)
                )
                Text(
                    text = stringResource(Res.string.detail_splits_pace),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(76.dp)
                )
                Text(
                    text = stringResource(Res.string.detail_splits_time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(64.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // 各スプリット行
            splits.forEach { split ->
                val isFastest = split.paceSeconds > 0.0 && split.paceSeconds <= fastestPace + 0.001
                
                // バーの長さ比率：速いほど長く（最速が 1.0、遅いほど小さく）
                val barFraction = if (split.paceSeconds > 0.0 && fastestPace > 0.0) {
                    ((fastestPace / split.paceSeconds).toFloat()).coerceIn(0.25f, 1.0f)
                } else {
                    0.25f
                }

                val distLabel = if (split.isLastPartial) {
                    val distUnit = if (isMetric) split.distanceMeters / 1000.0 else split.distanceMeters / 1609.344
                    val formatted = ((distUnit * 100).roundToInt() / 100.0).toString()
                    "$formatted $unitLabel"
                } else {
                    "${split.splitNumber} $unitLabel"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 距離
                    Text(
                        text = distLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isFastest) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(60.dp)
                    )

                    // ペース
                    Column(modifier = Modifier.width(76.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = FormatUtils.formatPace(split.paceSeconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isFastest) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isFastest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isFastest && splits.size > 1) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = stringResource(Res.string.detail_splits_fastest),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        split.avgHeartRate?.let { hr ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFE91E63),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${hr.roundToInt()} bpm",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // タイム（所要時間）
                    Text(
                        text = FormatUtils.formatDuration(split.durationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp)
                    )

                    // ペースバー（横棒）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barFraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isFastest && splits.size > 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
