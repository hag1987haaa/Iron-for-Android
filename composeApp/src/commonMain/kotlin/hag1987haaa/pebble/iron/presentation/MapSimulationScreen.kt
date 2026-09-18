package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import hag1987haaa.pebble.iron.util.GpxImporter
import hag1987haaa.pebble.iron.util.GpxCourse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapSimulationScreen(
    actions: AppActions,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    val pebblePlatform by viewModel.pebblePlatform.collectAsState()
    val stats by RunState.currentStats.collectAsState()
    val engineStats by KmpDependencies.trackerEngine.statistics.collectAsState()

    val settings = KmpDependencies.appSettings
    var gpxCourses by remember { mutableStateOf(settings.savedGpxCourses) }
    var pendingCourseForImport by remember { mutableStateOf<GpxCourse?>(null) }
    var editingCourse by remember { mutableStateOf<GpxCourse?>(null) }
    var courseNameInput by remember { mutableStateOf("") }

    fun validateCourseName(input: String, currentCourseId: String? = null): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty"
        if (trimmed.length > 12) return "Max 12 characters"
        if (!Regex("^[a-zA-Z0-9_ -]+$").matches(trimmed)) return "Only A-Z, 0-9, -, _, space allowed"
        val isDuplicate = gpxCourses.any { it.id != currentCourseId && it.name.equals(trimmed, ignoreCase = true) }
        if (isDuplicate) return "Course name already exists"
        return null
    }

    val activePlannedPoints = remember(gpxCourses) {
        gpxCourses.filter { it.isEnabled }.flatMap { it.points }
    }

    val currentLoc = stats.currentLocation ?: engineStats.currentLocation
    val displayPoints = if (stats.route.isNotEmpty()) {
        stats.route
    } else if (activePlannedPoints.isNotEmpty()) {
        activePlannedPoints
    } else {
        currentLoc?.let { listOf(it) } ?: emptyList()
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = KmpDependencies.trackerEngine.pebbleMessenger

    // メモリ保持のみ: 画面離脱時は予定ルートを自動クリア
    LaunchedEffect(activePlannedPoints) {
        messenger?.setPlannedCourse(activePlannedPoints.ifEmpty { null })
    }

    // コース一覧の変更時に設定へ永続保存＆Pebbleへ一括区切り文字列を自動同期
    LaunchedEffect(gpxCourses) {
        settings.savedGpxCourses = gpxCourses
        if (gpxCourses.isNotEmpty()) {
            val coursesDataStr = gpxCourses.joinToString("|") { "${if (it.isEnabled) 1 else 0},${it.name}" }
            messenger?.sendCoursesData(coursesDataStr)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            messenger?.setPlannedCourse(null)
        }
    }

    // コース名前入力・編集ダイアログ
    if (pendingCourseForImport != null || editingCourse != null) {
        val isNew = pendingCourseForImport != null
        val currentId = editingCourse?.id
        val errorMsg = validateCourseName(courseNameInput, currentId)

        AlertDialog(
            onDismissRequest = {
                pendingCourseForImport = null
                editingCourse = null
            },
            title = { Text(if (isNew) "Name New Course" else "Rename Course") },
            text = {
                Column {
                    Text(
                        "Enter course name (max 12 alphanumeric chars). Must be unique.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = courseNameInput,
                        onValueChange = {
                            if (it.length <= 12) courseNameInput = it
                        },
                        label = { Text("Course Name") },
                        singleLine = true,
                        isError = errorMsg != null,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                                Text("${courseNameInput.length}/12")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = courseNameInput.trim()
                        if (isNew) {
                            val newCourse = pendingCourseForImport!!.copy(name = sanitized)
                            gpxCourses = gpxCourses + newCourse
                            scope.launch {
                                snackbarHostState.showSnackbar("Added: $sanitized (${newCourse.points.size} pts)")
                            }
                            pendingCourseForImport = null
                        } else {
                            gpxCourses = gpxCourses.map {
                                if (it.id == editingCourse!!.id) it.copy(name = sanitized) else it
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Renamed to: $sanitized")
                            }
                            editingCourse = null
                        }
                    },
                    enabled = errorMsg == null
                ) {
                    Text(if (isNew) "Add" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingCourseForImport = null
                        editingCourse = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    var simulatedZoom by remember { mutableStateOf(16) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pebble Resolution Simulation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (pebblePlatform != null) {
                val (nativeMapW, nativeMapH) = getMapSizeForPlatform(pebblePlatform)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (messenger != null) {
                                    messenger.setMapZoom(simulatedZoom)
                                    messenger.sendMap(displayPoints, nativeMapW, nativeMapH, simulatedZoom)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to $pebblePlatform ($nativeMapW x $nativeMapH)!")
                                    }
                                }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Connected Device", style = MaterialTheme.typography.labelSmall)
                            Text(pebblePlatform!!, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("(Long press to send map)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            var isSwipePanMode by remember { mutableStateOf(KmpDependencies.appSettings.isMapSwipePanEnabled) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Map Swipe Action", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Touch action on Pebble Time 2 (Emery) & Gabbro during map view",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isSwipePanMode,
                            onCheckedChange = { checked ->
                                isSwipePanMode = checked
                                KmpDependencies.appSettings.isMapSwipePanEnabled = checked
                                KmpDependencies.appSettings.save()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isSwipePanMode) "Current: Map Pan (Scroll Map)" else "Current: Music Control (Prev/Next/Vol)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            var isAutoShowMapOnReady by remember { mutableStateOf(KmpDependencies.appSettings.isAutoShowMapOnReadyEnabled) }
            var autoCloseTimeout by remember { mutableStateOf(KmpDependencies.appSettings.mapAutoCloseTimeoutSeconds) }
            var isTimeoutDropdownExpanded by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-show Map on Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Automatically open map on watch when GPS signal is fixed (Ready)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isAutoShowMapOnReady,
                            onCheckedChange = { checked ->
                                isAutoShowMapOnReady = checked
                                KmpDependencies.appSettings.isAutoShowMapOnReadyEnabled = checked
                                KmpDependencies.appSettings.save()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isAutoShowMapOnReady) "Current: Enabled (Auto-display on Ready)" else "Current: Disabled (Default)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Map Auto-Close Timeout Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Map Auto-Close Timeout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Automatically return to cockpit screen when no map interaction occurs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Box {
                            TextButton(onClick = { isTimeoutDropdownExpanded = true }) {
                                Text(
                                    text = if (autoCloseTimeout == 0) "OFF" else "${autoCloseTimeout}s",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = isTimeoutDropdownExpanded,
                                onDismissRequest = { isTimeoutDropdownExpanded = false }
                            ) {
                                listOf(0, 5, 10, 15, 20, 30).forEach { sec ->
                                    DropdownMenuItem(
                                        text = { Text(if (sec == 0) "OFF (No auto-close)" else "$sec seconds") },
                                        onClick = {
                                            autoCloseTimeout = sec
                                            KmpDependencies.appSettings.mapAutoCloseTimeoutSeconds = sec
                                            KmpDependencies.appSettings.save()
                                            isTimeoutDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (autoCloseTimeout == 0) "Current: Disabled (Manual exit only)" else "Current: Auto-close after ${autoCloseTimeout}s of inactivity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // GPX Course (Planned Route) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPX Planned Courses (${gpxCourses.size}/20)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Manage up to 20 courses. Toggle [✓] to display on map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (gpxCourses.isEmpty()) {
                        Text(
                            "No courses imported yet. Add a GPX file to preview planned tracks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gpxCourses.forEach { course ->
                                Surface(
                                    color = if (course.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (course.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = course.isEnabled,
                                            onCheckedChange = { isChecked ->
                                                gpxCourses = gpxCourses.map {
                                                    if (it.id == course.id) it.copy(isEnabled = isChecked) else it
                                                }
                                            }
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = course.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (course.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${course.points.size} pts  •  ${formatGpxDistance(course.totalDistanceMeters)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                editingCourse = course
                                                courseNameInput = course.name
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename Course", modifier = Modifier.size(20.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                gpxCourses = gpxCourses.filter { it.id != course.id }
                                                scope.launch { snackbarHostState.showSnackbar("Removed course: ${course.name}") }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Course", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (gpxCourses.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    gpxCourses = emptyList()
                                    scope.launch { snackbarHostState.showSnackbar("All GPX courses cleared") }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Clear All")
                            }
                        }

                        if (gpxCourses.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val coursesDataStr = gpxCourses.joinToString("|") { "${if (it.isEnabled) 1 else 0},${it.name}" }
                                    messenger?.sendCoursesData(coursesDataStr)
                                    scope.launch { snackbarHostState.showSnackbar("Synced ${gpxCourses.size} courses to Pebble!") }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Sync")
                            }
                        }

                        Button(
                            onClick = {
                                actions.pickGpxFile { content ->
                                    val course = GpxImporter.parse(content)
                                    if (course != null && course.points.isNotEmpty()) {
                                        // 自動サニタイズされた初期名をセットし、入力ダイアログを開く
                                        val uniqueName = generateUniqueCourseName(course.name, gpxCourses)
                                        pendingCourseForImport = course
                                        courseNameInput = uniqueName
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Failed to parse GPX file or track is empty.")
                                        }
                                    }
                                }
                            },
                            enabled = gpxCourses.size < 20,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (gpxCourses.size < 20) "Add Course" else "Limit Reached")
                        }
                    }
                }
            }

            Text(
                "Pebble Display Mirroring Simulation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Zoom: Level $simulatedZoom",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val zoomDesc = when (simulatedZoom) {
                            11 -> "Regional (~40km, Highway only)"
                            12 -> "City-Wide (~20km, Highway only)"
                            13 -> "Ultra-Wide (~10km, Highway only)"
                            14 -> "Wide (~5km, Highway only)"
                            15 -> "Medium (~2.5km, Main roads)"
                            16 -> "Standard (~1.2km, Default)"
                            17 -> "Detail (~600m, All streets)"
                            18 -> "Max Detail (~300m)"
                            else -> ""
                        }
                        Text(
                            zoomDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(11, 12, 13, 14, 15, 16, 17, 18).forEach { z ->
                            FilterChip(
                                selected = (simulatedZoom == z),
                                onClick = {
                                    simulatedZoom = z
                                    messenger?.setMapZoom(z)
                                },
                                label = { Text("z$z", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            ResolutionPreview(
                name = "Pebble Classic / Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Classic") == true,
                isMonochrome = true,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble Classic/Steel!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time / Time Steel",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Time") == true && 
                             pebblePlatform?.contains("Round") == false && 
                             pebblePlatform?.contains("2") == false,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble Time!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Round 2 (260x260 Model)",
                width = 260,
                height = 260,
                mapWidth = 260,
                mapHeight = 198,
                isRound = true,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Round 2") == true,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble Round 2!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time Round",
                width = 180,
                height = 180,
                mapWidth = 180,
                mapHeight = 136,
                isRound = true,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Round") == true && pebblePlatform?.contains("Round 2") == false,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble Time Round!") }
                }
            )

            ResolutionPreview(
                name = "Pebble 2",
                width = 144,
                height = 168,
                mapWidth = 144,
                mapHeight = 128,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Pebble 2") == true,
                isMonochrome = true,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble 2!") }
                }
            )

            ResolutionPreview(
                name = "Pebble Time 2 (Prototype)",
                width = 200,
                height = 228,
                mapWidth = 200,
                mapHeight = 176,
                points = displayPoints,
                isHighlight = pebblePlatform?.contains("Time 2") == true,
                messenger = messenger,
                refreshKey = activePlannedPoints,
                zoom = simulatedZoom,
                onSendMap = { w, h ->
                    messenger?.setMapZoom(simulatedZoom)
                    messenger?.sendMap(displayPoints, w, h, simulatedZoom)
                    scope.launch { snackbarHostState.showSnackbar("Map (z$simulatedZoom) sent to Pebble Time 2!") }
                }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatGpxDistance(meters: Double): String {
    return if (meters >= 1000.0) {
        val km = meters / 1000.0
        val kmStr = ((km * 10).toInt() / 10.0).toString()
        "$kmStr km"
    } else {
        "${meters.toInt()} m"
    }
}

private fun getMapSizeForPlatform(platform: String?): Pair<Int, Int> {
    return when {
        platform?.contains("Classic") == true || (platform?.contains("Time") == true && !platform.contains("Round") && !platform.contains("2")) || platform?.contains("Pebble 2") == true -> Pair(144, 128)
        platform?.contains("Round 2") == true -> Pair(260, 198)
        platform?.contains("Round") == true -> Pair(180, 136)
        platform?.contains("Time 2") == true -> Pair(200, 176)
        else -> Pair(144, 128)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResolutionPreview(
    name: String,
    width: Int,
    height: Int,
    mapWidth: Int,
    mapHeight: Int,
    isRound: Boolean = false,
    points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>,
    isHighlight: Boolean = false,
    isMonochrome: Boolean = false,
    messenger: PebbleMessenger?,
    refreshKey: Any? = null,
    zoom: Int = 16,
    onSendMap: (Int, Int) -> Unit
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var rleBytes by remember { mutableStateOf(0) }
    var roadPct by remember { mutableStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(points, mapWidth, mapHeight, isMonochrome, refreshKey, zoom) {
        if (messenger != null && points.isNotEmpty()) {
            isLoading = true
            try {
                val info = messenger.getMapPreviewInfo(points, mapWidth, mapHeight, isMonochrome, zoom)
                if (info != null) {
                    previewImage = platformImageBitmapConverter?.invoke(mapWidth, mapHeight, info.rgba)
                    rleBytes = info.rleBytesCount
                    roadPct = info.roadPixelPercent
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (isHighlight) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$name (${width}x${height})${if (isMonochrome) " [B/W]" else ""}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        val shape = if (isRound) CircleShape else RectangleShape
        
        val panelHeight = height - mapHeight
        Box(
            modifier = Modifier
                .size(width.dp, height.dp)
                .border(if (isHighlight) 4.dp else 2.dp, if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .background(Color.Black)
                .clip(shape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onSendMap(mapWidth, mapHeight) }
                )
        ) {
            // マップ領域（実機 Pebble 同様に上部配置）
            Box(
                modifier = Modifier
                    .size(mapWidth.dp, mapHeight.dp)
                    .align(Alignment.TopCenter)
            ) {
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage!!,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1E1E))) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Waiting for GPS...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                
                // マップ中心の十字線（実機の現在地マーカー位置と完全一致）
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.25f)).align(Alignment.Center))
                Box(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.25f)).align(Alignment.Center))
            }

            // 実機同様の下部情報パネル領域（TIME / DIST）
            if (panelHeight > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(panelHeight.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black)
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray).align(Alignment.TopCenter))
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TIME", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                        Text("DIST", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (rleBytes > 0) {
            Spacer(Modifier.height(6.dp))
            val roadRounded = (roadPct * 10).toInt() / 10.0
            val isFast = rleBytes < 3500
            Surface(
                color = if (isFast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = "RLE: ${rleBytes} B | Road: ${roadRounded}% | ${if (isFast) "⚡ Fast (~0.3s)" else "⚠️ Large (~1-2s)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

var platformImageBitmapConverter: ((width: Int, height: Int, rgba: IntArray) -> ImageBitmap?)? = null

private fun generateUniqueCourseName(baseName: String, existing: List<GpxCourse>): String {
    var candidate = baseName.take(12).ifBlank { "COURSE" }
    if (!existing.any { it.name.equals(candidate, ignoreCase = true) }) {
        return candidate
    }
    for (i in 1..99) {
        val suffix = "_$i"
        val maxPrefixLen = (12 - suffix.length).coerceAtLeast(1)
        candidate = candidate.take(maxPrefixLen) + suffix
        if (!existing.any { it.name.equals(candidate, ignoreCase = true) }) {
            return candidate
        }
    }
    return candidate.take(12)
}
