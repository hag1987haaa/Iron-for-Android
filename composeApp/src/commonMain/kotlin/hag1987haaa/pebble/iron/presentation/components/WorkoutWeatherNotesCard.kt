package hag1987haaa.pebble.iron.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.WeatherCondition
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val MS_TO_MPH = 2.23694

fun formatTemperatureInput(tempC: Double?, isMetric: Boolean): String {
    if (tempC == null) return ""
    val value = if (isMetric) tempC else (tempC * 9.0 / 5.0 + 32.0)
    val roundedTenths = (value * 10.0).roundToInt()
    return if (roundedTenths % 10 == 0) {
        (roundedTenths / 10).toString()
    } else {
        (roundedTenths / 10.0).toString()
    }
}

fun parseTemperatureToCelsius(input: String, isMetric: Boolean): Double? {
    val trimmed = input.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    val parsed = trimmed.toDoubleOrNull() ?: return null
    val tempC = if (isMetric) parsed else (parsed - 32.0) * 5.0 / 9.0
    if (tempC !in -80.0..80.0) return null
    return (tempC * 10.0).roundToInt() / 10.0
}

fun formatWindSpeedInput(windSpeedMs: Double?, isMetric: Boolean): String {
    if (windSpeedMs == null) return ""
    val value = if (isMetric) windSpeedMs else (windSpeedMs * MS_TO_MPH)
    val roundedTenths = (value * 10.0).roundToInt()
    return if (roundedTenths % 10 == 0) {
        (roundedTenths / 10).toString()
    } else {
        (roundedTenths / 10.0).toString()
    }
}

fun parseWindSpeedToMs(input: String, isMetric: Boolean): Double? {
    val trimmed = input.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    val parsed = trimmed.toDoubleOrNull() ?: return null
    val speedMs = if (isMetric) parsed else (parsed / MS_TO_MPH)
    if (speedMs !in 0.0..150.0) return null
    return (speedMs * 10.0).roundToInt() / 10.0
}

@Composable
private fun WeatherCondition.displayLabel(): String = when (this) {
    WeatherCondition.SUNNY -> stringResource(Res.string.detail_weather_sunny)
    WeatherCondition.CLOUDY -> stringResource(Res.string.detail_weather_cloudy)
    WeatherCondition.RAINY -> stringResource(Res.string.detail_weather_rainy)
    WeatherCondition.SNOWY -> stringResource(Res.string.detail_weather_snowy)
}

private fun WeatherCondition.displayIcon(): ImageVector = when (this) {
    WeatherCondition.SUNNY -> Icons.Default.WbSunny
    WeatherCondition.CLOUDY -> Icons.Default.Cloud
    WeatherCondition.RAINY -> Icons.Default.WaterDrop
    WeatherCondition.SNOWY -> Icons.Default.AcUnit
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutWeatherNotesCard(
    selectedWeather: WeatherCondition?,
    onWeatherChange: (WeatherCondition?) -> Unit,
    temperatureInput: String,
    onTemperatureInputChange: (String) -> Unit,
    windSpeedInput: String,
    onWindSpeedInputChange: (String) -> Unit,
    notesInput: String,
    onNotesInputChange: (String) -> Unit,
    isMetric: Boolean,
    isConfirmMode: Boolean,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tempUnitSuffix = if (isMetric) "°C" else "°F"
    val windUnitSuffix = if (isMetric) "m/s" else "mph"
    val parsedTempC = remember(temperatureInput, isMetric) {
        parseTemperatureToCelsius(temperatureInput, isMetric)
    }
    val parsedWindMs = remember(windSpeedInput, isMetric) {
        parseWindSpeedToMs(windSpeedInput, isMetric)
    }
    val hasAnyValue = selectedWeather != null || parsedTempC != null || parsedWindMs != null || notesInput.isNotBlank()

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.detail_weather_notes_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isConfirmMode && !isEditing) {
                    TextButton(
                        onClick = onStartEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.detail_edit))
                    }
                }
            }

            if (isEditing || isConfirmMode) {
                Spacer(modifier = Modifier.height(10.dp))

                // Weather condition chips
                Text(
                    text = stringResource(Res.string.detail_weather_condition_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WeatherCondition.entries.forEach { condition ->
                        val selected = selectedWeather == condition
                        FilterChip(
                            selected = selected,
                            onClick = { onWeatherChange(if (selected) null else condition) },
                            label = { Text(condition.displayLabel()) },
                            leadingIcon = {
                                Icon(
                                    imageVector = condition.displayIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Temperature & Wind Speed side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() || it == '.' || it == '-' || it == ',' }
                            if (filtered.length <= 6) {
                                onTemperatureInputChange(filtered)
                            }
                        },
                        label = { Text(stringResource(Res.string.detail_weather_temp_label)) },
                        suffix = { Text(tempUnitSuffix) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = windSpeedInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }
                            if (filtered.length <= 6) {
                                onWindSpeedInputChange(filtered)
                            }
                        },
                        label = { Text(stringResource(Res.string.detail_weather_wind_label)) },
                        suffix = { Text(windUnitSuffix) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes text field
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = onNotesInputChange,
                    label = { Text(stringResource(Res.string.detail_notes_label)) },
                    placeholder = { Text(stringResource(Res.string.detail_notes_placeholder)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isConfirmMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onCancelEdit) {
                            Text(stringResource(Res.string.detail_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onSaveEdit) {
                            Text(stringResource(Res.string.detail_save))
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                if (!hasAnyValue) {
                    Text(
                        text = stringResource(Res.string.detail_weather_notes_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    if (selectedWeather != null || parsedTempC != null || parsedWindMs != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedWeather?.let { condition ->
                                AssistChip(
                                    onClick = onStartEdit,
                                    label = { Text(condition.displayLabel()) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = condition.displayIcon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            parsedTempC?.let { tempC ->
                                AssistChip(
                                    onClick = onStartEdit,
                                    label = { Text("${formatTemperatureInput(tempC, isMetric)}$tempUnitSuffix") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Thermostat,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            parsedWindMs?.let { speedMs ->
                                AssistChip(
                                    onClick = onStartEdit,
                                    label = { Text("${formatWindSpeedInput(speedMs, isMetric)} $windUnitSuffix") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Air,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    if (notesInput.isNotBlank()) {
                        if (selectedWeather != null || parsedTempC != null || parsedWindMs != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = notesInput.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
