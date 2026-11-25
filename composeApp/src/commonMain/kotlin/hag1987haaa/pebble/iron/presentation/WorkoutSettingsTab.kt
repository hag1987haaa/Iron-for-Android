package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.WorkoutTypeRegistry
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.util.getDisplayName
import hag1987haaa.pebble.iron.util.getPastelColor
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutSettingsTab(viewModel: SettingsViewModel) {
    val enabledActivityTypeNames by viewModel.enabledActivityTypes.collectAsState()
    val activityMidTypes by viewModel.activityMidTypes.collectAsState()
    val activityLowerTypes by viewModel.activityLowerTypes.collectAsState()
    val activityHrZoneEnabled by viewModel.activityHrZoneEnabled.collectAsState()
    val activityHrZoneMins by viewModel.activityHrZoneMins.collectAsState()
    val activityHrZoneMaxs by viewModel.activityHrZoneMaxs.collectAsState()
    val activityMapZooms by viewModel.activityMapZooms.collectAsState()

    val enabledTypes = remember(enabledActivityTypeNames) {
        enabledActivityTypeNames.mapNotNull { name ->
            ActivityType.entries.find { it.name == name }
        }.ifEmpty { ActivityType.entries }
    }

    var isOrderExpanded by remember { mutableStateOf(false) }
    var isMidDataExpanded by remember { mutableStateOf(false) }
    var isLowerDataExpanded by remember { mutableStateOf(false) }
    var selectedActivityType by remember { mutableStateOf(enabledTypes.firstOrNull() ?: ActivityType.RUNNING) }

    val currentMidTypes = activityMidTypes[selectedActivityType.name]
        ?: AppSettings.getDefaultMidTypes(selectedActivityType)
    val currentLowerTypes = activityLowerTypes[selectedActivityType.name]
        ?: AppSettings.getDefaultLowerTypes(selectedActivityType)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ============================================================
        // 1. 表示するワークアウト種別と並び順（Pebble & ホーム画面同期）
        // ============================================================
        item(key = "workout_order_section") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ExpandableSubSection(
                    title = "${stringResource(Res.string.settings_workout_order_title)} (${enabledTypes.size}/${ActivityType.entries.size})",
                    expanded = isOrderExpanded,
                    onToggle = { isOrderExpanded = !isOrderExpanded }
                ) {
                    WorkoutOrderSettingsContent(
                        enabledTypes = enabledTypes,
                        onOrderChanged = { viewModel.updateEnabledActivityTypes(it) },
                        onResetRequested = { viewModel.resetEnabledActivityTypes() }
                    )
                }
            }
        }

        // ============================================================
        // 2. ワークアウト別設定（プロフィール）- スティッキーヘッダー付き
        // ============================================================
        stickyHeader(key = "workout_profile_header") {
            WorkoutProfileStickyHeader(
                selectedActivityType = selectedActivityType,
                enabledTypes = enabledTypes,
                activityHrZoneEnabled = activityHrZoneEnabled,
                onActivityTypeSelected = { selectedActivityType = it }
            )
        }

        item(key = "workout_profile_desc") {
            Text(
                text = stringResource(Res.string.settings_workout_profile_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item(key = "workout_profile_card") {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                WorkoutProfileCardContent(
                    selectedActivityType = selectedActivityType,
                    activityHrZoneEnabled = activityHrZoneEnabled,
                    activityHrZoneMins = activityHrZoneMins,
                    activityHrZoneMaxs = activityHrZoneMaxs,
                    activityMapZooms = activityMapZooms,
                    viewModel = viewModel
                )
            }
        }

        item(key = "workout_profile_mid_data") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ExpandableSubSection(
                    title = "${stringResource(Res.string.settings_section_mid_data)} (${currentMidTypes.size})",
                    expanded = isMidDataExpanded,
                    onToggle = { isMidDataExpanded = !isMidDataExpanded }
                ) {
                    MidDataSettingsContent(activityMidTypes, selectedActivityType, viewModel)
                }
            }
        }

        item(key = "workout_profile_lower_data") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ExpandableSubSection(
                    title = "${stringResource(Res.string.settings_section_lower_data)} (${currentLowerTypes.size})",
                    expanded = isLowerDataExpanded,
                    onToggle = { isLowerDataExpanded = !isLowerDataExpanded }
                ) {
                    LowerDataSettingsContent(activityLowerTypes, selectedActivityType, viewModel)
                }
            }
        }
    }
}

@Composable
private fun WorkoutOrderSettingsContent(
    enabledTypes: List<ActivityType>,
    onOrderChanged: (List<ActivityType>) -> Unit,
    onResetRequested: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_workout_order_reset_title)) },
            text = { Text(stringResource(Res.string.settings_workout_order_reset_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetRequested()
                    showResetDialog = false
                }) {
                    Text(stringResource(Res.string.settings_button_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(Res.string.settings_button_cancel))
                }
            }
        )
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_workout_order_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { showResetDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.settings_reset_to_default), style = MaterialTheme.typography.labelSmall)
            }
        }

        enabledTypes.forEachIndexed { index, type ->
            val isDragging = (draggingIndex == index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            shadowElevation = 8f
                        }
                    }
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 長押しドラッグハンドル
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .pointerInput(enabledTypes, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    val targetIdx = (currentIdx + (dragOffsetY / itemHeightPx).toInt())
                                        .coerceIn(0, enabledTypes.size - 1)
                                    if (targetIdx != currentIdx) {
                                        val mutable = enabledTypes.toMutableList()
                                        val item = mutable.removeAt(currentIdx)
                                        mutable.add(targetIdx, item)
                                        onOrderChanged(mutable)
                                        draggingIndex = targetIdx
                                        dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 非表示にするボタン（最低1件は残す）
                IconButton(
                    onClick = {
                        if (enabledTypes.size > 1) {
                            val list = enabledTypes.toMutableList()
                            list.remove(type)
                            onOrderChanged(list)
                        }
                    },
                    enabled = enabledTypes.size > 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RemoveCircle,
                        contentDescription = null,
                        tint = if (enabledTypes.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(type.getPastelColor(), CircleShape)
                )
                Spacer(Modifier.width(8.dp))

                Text(
                    text = type.getDisplayName(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = {
                        if (index > 0) {
                            val list = enabledTypes.toMutableList()
                            val t = list[index]
                            list[index] = list[index - 1]
                            list[index - 1] = t
                            onOrderChanged(list)
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        if (index < enabledTypes.size - 1) {
                            val list = enabledTypes.toMutableList()
                            val t = list[index]
                            list[index] = list[index + 1]
                            list[index + 1] = t
                            onOrderChanged(list)
                        }
                    },
                    enabled = index < enabledTypes.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp))
                }
            }
        }

        val disabledTypes = ActivityType.entries.filter { it !in enabledTypes }
        if (disabledTypes.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = stringResource(Res.string.settings_workout_hidden_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
            disabledTypes.forEach { type ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val list = enabledTypes.toMutableList()
                            list.add(type)
                            onOrderChanged(list)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(type.getPastelColor(), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = type.getDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutProfileStickyHeader(
    selectedActivityType: ActivityType,
    enabledTypes: List<ActivityType>,
    activityHrZoneEnabled: Map<String, Boolean>,
    onActivityTypeSelected: (ActivityType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hiddenTypes = remember(enabledTypes) {
        ActivityType.entries.filter { it !in enabledTypes }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.settings_section_workout_profile),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(Res.string.settings_workout_profile_editing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(selectedActivityType.getPastelColor(), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selectedActivityType.getDisplayName(),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        enabledTypes.forEach { type ->
                            ProfileDropdownItem(
                                type = type,
                                isSelected = type == selectedActivityType,
                                isHrEnabled = activityHrZoneEnabled[type.name] == true,
                                isHidden = false,
                                onClick = {
                                    onActivityTypeSelected(type)
                                    expanded = false
                                }
                            )
                        }
                        if (hiddenTypes.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            hiddenTypes.forEach { type ->
                                ProfileDropdownItem(
                                    type = type,
                                    isSelected = type == selectedActivityType,
                                    isHrEnabled = activityHrZoneEnabled[type.name] == true,
                                    isHidden = true,
                                    onClick = {
                                        onActivityTypeSelected(type)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ProfileDropdownItem(
    type: ActivityType,
    isSelected: Boolean,
    isHrEnabled: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(type.getPastelColor(), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = type.getDisplayName(),
                    style = if (isSelected) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isHidden -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                if (isHrEnabled) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun WorkoutProfileCardContent(
    selectedActivityType: ActivityType,
    activityHrZoneEnabled: Map<String, Boolean>,
    activityHrZoneMins: Map<String, Int>,
    activityHrZoneMaxs: Map<String, Int>,
    activityMapZooms: Map<String, Int>,
    viewModel: SettingsViewModel
) {
    val defaults = WorkoutTypeRegistry.getDefinition(selectedActivityType).watchDefaults
    val isHrZoneEnabled = activityHrZoneEnabled[selectedActivityType.name] ?: false
    val currentMin = activityHrZoneMins[selectedActivityType.name] ?: defaults.hrZoneMin
    val currentMax = activityHrZoneMaxs[selectedActivityType.name] ?: defaults.hrZoneMax
    val currentZoom = activityMapZooms[selectedActivityType.name] ?: defaults.mapZoom

    Column(modifier = Modifier.padding(14.dp)) {
        // 1. 心拍ゾーン通知（種別ごと ON/OFF + Min/Max BPM）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_notif_hr_zone_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.settings_notif_hr_zone_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = isHrZoneEnabled,
                onCheckedChange = { viewModel.updateActivityHrZoneEnabled(selectedActivityType, it) },
                modifier = Modifier.scale(0.75f)
            )
        }

        if (isHrZoneEnabled) {
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.settings_notif_hr_zone_min),
                    style = MaterialTheme.typography.bodyMedium
                )
                var minExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { minExpanded = true }) {
                        Text("$currentMin bpm")
                    }
                    DropdownMenu(expanded = minExpanded, onDismissRequest = { minExpanded = false }) {
                        (60..180 step 5).forEach { bpm ->
                            DropdownMenuItem(
                                text = { Text("$bpm bpm") },
                                onClick = {
                                    viewModel.updateActivityHrZoneMin(selectedActivityType, bpm)
                                    minExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.settings_notif_hr_zone_max),
                    style = MaterialTheme.typography.bodyMedium
                )
                var maxExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { maxExpanded = true }) {
                        Text("$currentMax bpm")
                    }
                    DropdownMenu(expanded = maxExpanded, onDismissRequest = { maxExpanded = false }) {
                        (90..210 step 5).forEach { bpm ->
                            DropdownMenuItem(
                                text = { Text("$bpm bpm") },
                                onClick = {
                                    viewModel.updateActivityHrZoneMax(selectedActivityType, bpm)
                                    maxExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(Res.string.settings_notif_hr_zone_diff_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // 2. マップ初期拡大率（種別ごと）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_profile_map_zoom_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.settings_map_zooms_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            var zoomExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { zoomExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(getZoomLevelLabel(currentZoom), style = MaterialTheme.typography.bodySmall)
                }
                DropdownMenu(
                    expanded = zoomExpanded,
                    onDismissRequest = { zoomExpanded = false }
                ) {
                    listOf(13, 14, 15, 16, 17, 18).forEach { z ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = getZoomLevelLabel(z),
                                    style = if (z == currentZoom) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = if (z == currentZoom) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                viewModel.updateActivityMapZoom(selectedActivityType, z)
                                zoomExpanded = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

        // 3. 他の種別から設定をコピー
        var showCopyDialog by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { showCopyDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.settings_profile_copy_button),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (showCopyDialog) {
            CopyWorkoutProfileDialog(
                targetActivityType = selectedActivityType,
                onDismiss = { showCopyDialog = false },
                onConfirm = { source, copyMid, copyLower, copyHrZone, copyMapZoom ->
                    viewModel.copyWorkoutProfile(
                        source = source,
                        target = selectedActivityType,
                        copyMid = copyMid,
                        copyLower = copyLower,
                        copyHrZone = copyHrZone,
                        copyMapZoom = copyMapZoom
                    )
                    showCopyDialog = false
                }
            )
        }
    }
}

@Composable
private fun CopyWorkoutProfileDialog(
    targetActivityType: ActivityType,
    onDismiss: () -> Unit,
    onConfirm: (source: ActivityType, copyMid: Boolean, copyLower: Boolean, copyHrZone: Boolean, copyMapZoom: Boolean) -> Unit
) {
    val candidates = remember(targetActivityType) {
        ActivityType.entries.filter { it != targetActivityType }
    }
    var selectedSource by remember(targetActivityType) {
        mutableStateOf(candidates.firstOrNull() ?: targetActivityType)
    }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var copyMid by remember { mutableStateOf(true) }
    var copyLower by remember { mutableStateOf(true) }
    var copyHrZone by remember { mutableStateOf(false) }
    var copyMapZoom by remember { mutableStateOf(false) }

    val hasAnySelection = copyMid || copyLower || copyHrZone || copyMapZoom

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_profile_copy_title, targetActivityType.getDisplayName()))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.settings_profile_copy_source_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { sourceDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(selectedSource.getPastelColor(), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(selectedSource.getDisplayName())
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = sourceDropdownExpanded,
                        onDismissRequest = { sourceDropdownExpanded = false }
                    ) {
                        candidates.forEach { candidate ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(candidate.getPastelColor(), CircleShape)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(candidate.getDisplayName())
                                    }
                                },
                                onClick = {
                                    selectedSource = candidate
                                    sourceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(Res.string.settings_profile_copy_items_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))

                CopyOptionCheckboxRow(
                    label = stringResource(Res.string.settings_section_mid_data),
                    checked = copyMid,
                    onCheckedChange = { copyMid = it }
                )
                CopyOptionCheckboxRow(
                    label = stringResource(Res.string.settings_section_lower_data),
                    checked = copyLower,
                    onCheckedChange = { copyLower = it }
                )
                CopyOptionCheckboxRow(
                    label = stringResource(Res.string.settings_notif_hr_zone_title),
                    checked = copyHrZone,
                    onCheckedChange = { copyHrZone = it }
                )
                CopyOptionCheckboxRow(
                    label = stringResource(Res.string.settings_profile_map_zoom_title),
                    checked = copyMapZoom,
                    onCheckedChange = { copyMapZoom = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedSource, copyMid, copyLower, copyHrZone, copyMapZoom) },
                enabled = hasAnySelection && selectedSource != targetActivityType
            ) {
                Text(stringResource(Res.string.settings_profile_copy_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_button_cancel))
            }
        }
    )
}

@Composable
private fun CopyOptionCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ReorderableDataSettingsList(
    activityType: ActivityType,
    enabledIds: List<Int>,
    allItems: List<Pair<Int, String>>,
    onOrderChanged: (List<Int>) -> Unit,
    onItemRemoved: (Int) -> Unit,
    onItemAdded: (Int) -> Unit,
    onResetRequested: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_reset_confirm_title, activityType.getDisplayName())) },
            text = { Text(stringResource(Res.string.settings_reset_confirm_desc, activityType.getDisplayName())) },
            confirmButton = {
                TextButton(onClick = {
                    onResetRequested()
                    showResetDialog = false
                }) {
                    Text(stringResource(Res.string.settings_button_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(Res.string.settings_button_cancel))
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val validCount = enabledIds.count { id -> allItems.any { it.first == id } }
        Text(
            text = "${activityType.getDisplayName()} ($validCount)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(
            onClick = { showResetDialog = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.settings_reset_to_default), style = MaterialTheme.typography.labelSmall)
        }
    }

    val validEnabledItems = enabledIds.mapNotNull { id ->
        val found = allItems.find { it.first == id }
        if (found != null) id to found.second else null
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        validEnabledItems.forEachIndexed { index, (typeId, name) ->
            val isDragging = (draggingIndex == index)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            shadowElevation = 8f
                        }
                    }
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .pointerInput(validEnabledItems, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    val targetIdx = (currentIdx + (dragOffsetY / itemHeightPx).toInt()).coerceIn(0, validEnabledItems.size - 1)
                                    if (targetIdx != currentIdx) {
                                        val mutable = validEnabledItems.map { it.first }.toMutableList()
                                        val item = mutable.removeAt(currentIdx)
                                        mutable.add(targetIdx, item)
                                        onOrderChanged(mutable)
                                        draggingIndex = targetIdx
                                        dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { onItemRemoved(typeId) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.RemoveCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = name,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = {
                        val list = validEnabledItems.map { it.first }.toMutableList()
                        val actIdx = list.indexOf(typeId)
                        if (actIdx > 0) {
                            val t = list[actIdx]
                            list[actIdx] = list[actIdx - 1]
                            list[actIdx - 1] = t
                            onOrderChanged(list)
                        }
                    },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        val list = validEnabledItems.map { it.first }.toMutableList()
                        val actIdx = list.indexOf(typeId)
                        if (actIdx != -1 && actIdx < list.size - 1) {
                            val t = list[actIdx]
                            list[actIdx] = list[actIdx + 1]
                            list[actIdx + 1] = t
                            onOrderChanged(list)
                        }
                    },
                    enabled = index < validEnabledItems.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp))
                }
            }
        }

        val disabledItems = allItems.filter { item -> validEnabledItems.none { it.first == item.first } }
        if (disabledItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            disabledItems.forEach { (typeId, name) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onItemAdded(typeId) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MidDataSettingsContent(
    activityMidTypes: Map<String, List<Int>>,
    selectedActivityType: ActivityType,
    viewModel: SettingsViewModel
) {
    val currentTypes = activityMidTypes[selectedActivityType.name] ?: AppSettings.getDefaultMidTypes(selectedActivityType)

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_mid_data_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(4.dp))

        val allItems = listOf(
            9 to stringResource(Res.string.settings_mid_item_clock),
            8 to stringResource(Res.string.settings_mid_item_speed),
            0 to stringResource(Res.string.settings_mid_item_pace),
            14 to stringResource(Res.string.settings_mid_item_pace_1m),
            15 to stringResource(Res.string.settings_mid_item_pace_2m),
            16 to stringResource(Res.string.settings_mid_item_pace_5m),
            17 to stringResource(Res.string.settings_mid_item_pace_10m),
            7 to stringResource(Res.string.settings_mid_item_avg_pace),
            1 to stringResource(Res.string.settings_mid_item_dist),
            2 to stringResource(Res.string.settings_mid_item_steps),
            3 to stringResource(Res.string.settings_mid_item_alt),
            4 to stringResource(Res.string.settings_mid_item_hr),
            12 to stringResource(Res.string.settings_mid_item_hr_ble),
            13 to stringResource(Res.string.settings_mid_item_hr_watch),
            5 to stringResource(Res.string.settings_mid_item_cal),
            10 to stringResource(Res.string.settings_mid_item_gain),
            11 to stringResource(Res.string.settings_mid_item_cadence),
            24 to stringResource(Res.string.settings_mid_item_cadence_nz),
            18 to stringResource(Res.string.settings_mid_item_power),
            19 to stringResource(Res.string.settings_mid_item_power_3s),
            20 to stringResource(Res.string.settings_mid_item_power_10s),
            21 to stringResource(Res.string.settings_mid_item_power_30s),
            22 to stringResource(Res.string.settings_mid_item_power_5m),
            23 to stringResource(Res.string.settings_mid_item_avg_power),
            99 to stringResource(Res.string.settings_mid_item_detail)
        )

        ReorderableDataSettingsList(
            activityType = selectedActivityType,
            enabledIds = currentTypes,
            allItems = allItems,
            onOrderChanged = { newList -> viewModel.updateMidDataSettings(selectedActivityType, newList) },
            onItemRemoved = { typeId ->
                val list = currentTypes.toMutableList()
                list.remove(typeId)
                viewModel.updateMidDataSettings(selectedActivityType, list)
            },
            onItemAdded = { typeId ->
                val list = currentTypes.toMutableList()
                list.add(typeId)
                viewModel.updateMidDataSettings(selectedActivityType, list)
            },
            onResetRequested = {
                viewModel.resetMidDataSettings(selectedActivityType)
            }
        )
    }
}

@Composable
fun LowerDataSettingsContent(
    activityLowerTypes: Map<String, List<Int>>,
    selectedActivityType: ActivityType,
    viewModel: SettingsViewModel
) {
    val currentTypes = activityLowerTypes[selectedActivityType.name] ?: AppSettings.getDefaultLowerTypes(selectedActivityType)

    Column(modifier = Modifier.padding(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_lower_data_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(4.dp))

        val allItems = listOf(
            9 to stringResource(Res.string.settings_mid_item_clock),
            8 to stringResource(Res.string.settings_mid_item_speed),
            0 to stringResource(Res.string.settings_mid_item_pace),
            14 to stringResource(Res.string.settings_mid_item_pace_1m),
            15 to stringResource(Res.string.settings_mid_item_pace_2m),
            16 to stringResource(Res.string.settings_mid_item_pace_5m),
            17 to stringResource(Res.string.settings_mid_item_pace_10m),
            7 to stringResource(Res.string.settings_mid_item_avg_pace),
            1 to stringResource(Res.string.settings_mid_item_dist),
            2 to stringResource(Res.string.settings_mid_item_steps),
            3 to stringResource(Res.string.settings_mid_item_alt),
            4 to stringResource(Res.string.settings_mid_item_hr),
            12 to stringResource(Res.string.settings_mid_item_hr_ble),
            13 to stringResource(Res.string.settings_mid_item_hr_watch),
            5 to stringResource(Res.string.settings_mid_item_cal),
            10 to stringResource(Res.string.settings_mid_item_gain),
            11 to stringResource(Res.string.settings_mid_item_cadence),
            24 to stringResource(Res.string.settings_mid_item_cadence_nz),
            18 to stringResource(Res.string.settings_mid_item_power),
            19 to stringResource(Res.string.settings_mid_item_power_3s),
            20 to stringResource(Res.string.settings_mid_item_power_10s),
            21 to stringResource(Res.string.settings_mid_item_power_30s),
            22 to stringResource(Res.string.settings_mid_item_power_5m),
            23 to stringResource(Res.string.settings_mid_item_avg_power),
            100 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_pace),
            101 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_dist),
            102 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_steps),
            103 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_alt),
            104 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_hr),
            105 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_cal),
            106 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_cadence),
            107 to stringResource(Res.string.settings_graph_prefix) + stringResource(Res.string.settings_mid_item_power)
        )

        ReorderableDataSettingsList(
            activityType = selectedActivityType,
            enabledIds = currentTypes,
            allItems = allItems,
            onOrderChanged = { newList -> viewModel.updateLowerDataSettings(selectedActivityType, newList) },
            onItemRemoved = { typeId ->
                val list = currentTypes.toMutableList()
                list.remove(typeId)
                viewModel.updateLowerDataSettings(selectedActivityType, list)
            },
            onItemAdded = { typeId ->
                val list = currentTypes.toMutableList()
                list.add(typeId)
                viewModel.updateLowerDataSettings(selectedActivityType, list)
            },
            onResetRequested = {
                viewModel.resetLowerDataSettings(selectedActivityType)
            }
        )
    }
}

@Composable
private fun getZoomLevelLabel(zoom: Int): String {
    return when (zoom) {
        13 -> stringResource(Res.string.zoom_level_13)
        14 -> stringResource(Res.string.zoom_level_14)
        15 -> stringResource(Res.string.zoom_level_15)
        16 -> stringResource(Res.string.zoom_level_16)
        17 -> stringResource(Res.string.zoom_level_17)
        18 -> stringResource(Res.string.zoom_level_18)
        else -> "Zoom $zoom"
    }
}
