package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.util.GpxCourse
import hag1987haaa.pebble.iron.util.toActivePlannedPoints
import hag1987haaa.pebble.iron.util.GpxImporter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxCoursesSheet(
    actions: AppActions,
    onDismissRequest: () -> Unit
) {
    val settings = KmpDependencies.appSettings
    val savedCourses by settings.savedGpxCoursesFlow.collectAsState()
    var pendingCourseForImport by remember { mutableStateOf<GpxCourse?>(null) }
    var editingCourse by remember { mutableStateOf<GpxCourse?>(null) }
    var courseNameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val messenger = KmpDependencies.trackerEngine.pebbleMessenger
    val snackbarHostState = remember { SnackbarHostState() }

    fun updateCourses(newList: List<GpxCourse>) {
        settings.savedGpxCourses = newList
        val activePlannedPoints = newList.toActivePlannedPoints()
        messenger?.setPlannedCourse(activePlannedPoints.ifEmpty { null })
        val coursesDataStr = if (newList.isNotEmpty()) {
            newList.joinToString("|") { "${if (it.isEnabled) 1 else 0},${it.name}" }
        } else {
            ""
        }
        messenger?.sendCoursesData(coursesDataStr)
    }

    fun validateCourseName(input: String, currentCourseId: String? = null): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "Name cannot be empty"
        if (trimmed.length > 12) return "Max 12 characters"
        val forbiddenChars = listOf(',', '.', '|', ':', ';', '/', '\\')
        if (trimmed.any { it in forbiddenChars }) return "Commas, dots & symbols are forbidden"
        if (!Regex("^[a-zA-Z0-9_ -]+$").matches(trimmed)) return "Only A-Z, 0-9, -, _, space allowed"
        val isDuplicate = savedCourses.any { it.id != currentCourseId && it.name.equals(trimmed, ignoreCase = true) }
        if (isDuplicate) return "Course name already exists"
        return null
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
                        onValueChange = { input ->
                            val sanitized = input.filterNot { it in ",.|:;/\\" }
                            if (sanitized.length <= 12) courseNameInput = sanitized
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
                        val sanitizedName = courseNameInput.trim().take(12)
                        if (isNew && pendingCourseForImport != null) {
                            val newCourse = pendingCourseForImport!!.copy(name = sanitizedName, isEnabled = true)
                            val updated = savedCourses + newCourse
                            updateCourses(updated)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added course: $sanitizedName")
                            }
                            pendingCourseForImport = null
                        } else if (editingCourse != null) {
                            val updated = savedCourses.map {
                                if (it.id == editingCourse!!.id) it.copy(name = sanitizedName) else it
                            }
                            updateCourses(updated)
                            scope.launch {
                                snackbarHostState.showSnackbar("Renamed course to: $sanitizedName")
                            }
                            editingCourse = null
                        }
                    },
                    enabled = errorMsg == null && courseNameInput.trim().isNotEmpty()
                ) {
                    Text(if (isNew) "Add" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCourseForImport = null
                    editingCourse = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ヘッダー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.gpx_courses_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(Res.string.gpx_courses_sheet_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.map_snapshot_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // コース一覧
            if (savedCourses.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No GPX courses imported",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Tap 'Add Course' to import a .gpx route",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedCourses, key = { it.id }) { course ->
                        Surface(
                            color = if (course.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
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
                                        val updated = savedCourses.map {
                                            if (it.id == course.id) it.copy(isEnabled = isChecked) else it
                                        }
                                        updateCourses(updated)
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
                                        text = "${course.points.size} pts  •  ${formatGpxDistanceMeters(course.totalDistanceMeters)}",
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
                                        val updated = savedCourses.filter { it.id != course.id }
                                        updateCourses(updated)
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

            Spacer(Modifier.height(16.dp))

            // アクションボタン列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (savedCourses.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            updateCourses(emptyList())
                            scope.launch { snackbarHostState.showSnackbar("All GPX courses cleared") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val coursesDataStr = savedCourses.joinToString("|") { "${if (it.isEnabled) 1 else 0},${it.name}" }
                            messenger?.sendCoursesData(coursesDataStr)
                            scope.launch { snackbarHostState.showSnackbar("Synced ${savedCourses.size} courses to Pebble!") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sync", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = {
                        actions.pickGpxFile { content ->
                            val course = GpxImporter.parse(content)
                            if (course != null && course.points.isNotEmpty()) {
                                val uniqueName = generateUniqueGpxCourseName(course.name, savedCourses)
                                pendingCourseForImport = course
                                courseNameInput = uniqueName
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to parse GPX file or track is empty.")
                                }
                            }
                        }
                    },
                    enabled = savedCourses.size < 20,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (savedCourses.size < 20) "Add Course" else "Limit (20)", style = MaterialTheme.typography.labelMedium)
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

private fun formatGpxDistanceMeters(meters: Double): String {
    return if (meters >= 1000.0) {
        val km = meters / 1000.0
        val kmStr = ((km * 10).toInt() / 10.0).toString()
        "$kmStr km"
    } else {
        "${meters.toInt()} m"
    }
}

private fun generateUniqueGpxCourseName(baseName: String, existing: List<GpxCourse>): String {
    val forbiddenChars = setOf(',', '.', '|', ':', ';', '/', '\\')
    var candidate = baseName.map { if (it in forbiddenChars) '_' else it }.joinToString("")
    candidate = candidate.replace(Regex("[^a-zA-Z0-9_ -]"), "_").trim().take(12).ifBlank { "COURSE" }
    if (!existing.any { it.name.equals(candidate, ignoreCase = true) }) {
        return candidate
    }
    for (i in 1..99) {
        val suffix = "_$i"
        val maxPrefixLen = (12 - suffix.length).coerceAtLeast(1)
        val withSuffix = candidate.take(maxPrefixLen) + suffix
        if (!existing.any { it.name.equals(withSuffix, ignoreCase = true) }) {
            return withSuffix
        }
    }
    return candidate.take(12)
}
