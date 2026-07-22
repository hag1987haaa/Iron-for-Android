@file:OptIn(ExperimentalFoundationApi::class)

package hag1987haaa.pebble.iron.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.util.getDisplayName
import hag1987haaa.pebble.iron.util.getMonthName
import hag1987haaa.pebble.iron.util.getPastelColor
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryScreen(actions: AppActions, onRunSelected: (Long) -> Unit) {
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(KmpDependencies.runRepository) }
    val runs by viewModel.runs.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val calDate by viewModel.calendarDate.collectAsState()
    val specDate by viewModel.selectedSpecificDate.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val runToDelete = remember { mutableStateOf<Long?>(null) }

    if (runToDelete.value != null) {
        AlertDialog(
            onDismissRequest = { runToDelete.value = null },
            title = { Text(stringResource(Res.string.history_delete_title)) },
            text = { Text(stringResource(Res.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = { runToDelete.value?.let { actions.deleteRunRecord(it) }; runToDelete.value = null }) {
                    Text(stringResource(Res.string.history_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { runToDelete.value = null }) { Text(stringResource(Res.string.history_delete_cancel)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- View Mode Switcher ---
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            SegmentedButton(
                selected = viewMode == HistoryViewMode.SCROLL,
                onClick = { viewModel.setViewMode(HistoryViewMode.SCROLL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) { Text(stringResource(Res.string.history_view_scroll), fontSize = 12.sp) }
            SegmentedButton(
                selected = viewMode == HistoryViewMode.MONTHLY,
                onClick = { viewModel.setViewMode(HistoryViewMode.MONTHLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) { Text(stringResource(Res.string.history_view_monthly), fontSize = 12.sp) }
            SegmentedButton(
                selected = viewMode == HistoryViewMode.WEEKLY,
                onClick = { viewModel.setViewMode(HistoryViewMode.WEEKLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) { Text(stringResource(Res.string.history_view_weekly), fontSize = 12.sp) }
        }

        FilterPanel(viewModel = viewModel, filter = filter, availableYears = availableYears, viewMode = viewMode)

        if (viewMode == HistoryViewMode.MONTHLY) {
            MonthlyCalendarView(viewModel, calDate, specDate)
        } else if (viewMode == HistoryViewMode.WEEKLY) {
            WeeklyCalendarView(viewModel, calDate, specDate)
        }

        if (runs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.history_no_match), color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (viewMode == HistoryViewMode.SCROLL) {
                    val groupedRuns = runs.groupBy { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).year }
                        .mapValues { yEntry -> yEntry.value.groupBy { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber } }

                    groupedRuns.forEach { (year, months) ->
                        stickyHeader { YearHeader(year) }
                        months.forEach { (month, monthRuns) ->
                            stickyHeader { MonthHeader(month) }
                            items(monthRuns, key = { it.id }) { run ->
                                HistoryListItem(run, onRunSelected, onDelete = { runToDelete.value = run.id })
                            }
                        }
                    }
                } else {
                    items(runs, key = { it.id }) { run ->
                        HistoryListItem(run, onRunSelected, onDelete = { runToDelete.value = run.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        val yearStr = stringResource(Res.string.history_year_format).replace("%d", year.toString())
        Text(yearStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

@Composable
private fun MonthHeader(month: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)) {
        Text(getMonthName(month), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
    }
}

@Composable
private fun FilterPanel(viewModel: HistoryViewModel, filter: HistoryFilter, availableYears: List<Int>, viewMode: HistoryViewMode) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).animateContentSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FilterList, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.history_filter_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (filter != HistoryFilter()) {
                TextButton(onClick = { viewModel.clearFilters() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(Res.string.history_filter_clear), fontSize = 12.sp)
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                // Activity Type Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityType.entries.forEach { type ->
                        val isSelected = type in filter.selectedTypes
                        val isFilterActive = filter.selectedTypes.isNotEmpty()
                        val shouldShowColor = !isFilterActive || isSelected
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleTypeFilter(type) },
                            label = { Text(type.getDisplayName(), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = type.getPastelColor(),
                                selectedLabelColor = Color(0xFF333333),
                                containerColor = if (shouldShowColor) type.getPastelColor() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = if (shouldShowColor) Color(0xFF333333) else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                if (viewMode == HistoryViewMode.SCROLL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(Res.string.history_filter_range_start), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    YearMonthSelector(
                        year = filter.startYear, month = filter.startMonth, availableYears = availableYears,
                        onSelected = { y, m -> viewModel.setDateRange(y, m, filter.endYear, filter.endMonth) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(Res.string.history_filter_range_end), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    YearMonthSelector(
                        year = filter.endYear, month = filter.endMonth, availableYears = availableYears,
                        onSelected = { y, m -> viewModel.setDateRange(filter.startYear, filter.startMonth, y, m) },
                        quickActions = {
                            TextButton(onClick = { viewModel.setDateRange(filter.startYear, filter.startMonth, filter.startYear, filter.startMonth) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(Res.string.history_filter_range_same), fontSize = 10.sp) }
                            TextButton(onClick = { 
                                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                viewModel.setDateRange(filter.startYear, filter.startMonth, now.year, now.monthNumber)
                            }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(Res.string.history_filter_range_current), fontSize = 10.sp) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericFilterInput(
                        value = filter.minDistance,
                        isGreater = filter.isGreaterDistance,
                        label = stringResource(Res.string.history_filter_distance_min),
                        onToggle = { viewModel.toggleDistanceComparison() },
                        onValueChange = { viewModel.setDistanceFilter(it) },
                        isDistance = true,
                        modifier = Modifier.weight(1f)
                    )
                    NumericFilterInput(
                        value = filter.minCalories,
                        isGreater = filter.isGreaterCalories,
                        label = stringResource(Res.string.history_filter_calories_min),
                        onToggle = { viewModel.toggleCaloriesComparison() },
                        onValueChange = { viewModel.setCaloriesFilter(it) },
                        isDistance = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun YearMonthSelector(year: Int?, month: Int?, availableYears: List<Int>, onSelected: (Int?, Int?) -> Unit, quickActions: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        var yExp by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { yExp = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(32.dp)) {
                val yearStr = year?.toString() ?: stringResource(Res.string.history_filter_all_years)
                Text(yearStr, fontSize = 11.sp)
            }
            DropdownMenu(expanded = yExp, onDismissRequest = { yExp = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.history_filter_all_years)) }, onClick = { onSelected(null, month); yExp = false })
                availableYears.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onSelected(y, month); yExp = false }) }
            }
        }
        var mExp by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { mExp = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(32.dp)) {
                val monthStr = month?.let { getMonthName(it) } ?: stringResource(Res.string.history_filter_all_months)
                Text(monthStr, fontSize = 11.sp)
            }
            DropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.history_filter_all_months)) }, onClick = { onSelected(year, null); mExp = false })
                (1..12).forEach { m -> DropdownMenuItem(text = { Text(getMonthName(m)) }, onClick = { onSelected(year, m); mExp = false }) }
            }
        }
        quickActions()
    }
}

@Composable
private fun NumericFilterInput(value: Double?, isGreater: Boolean, label: String, onToggle: () -> Unit, onValueChange: (Double?) -> Unit, isDistance: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onToggle() },
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (isGreater) stringResource(Res.string.history_filter_comp_greater) else stringResource(Res.string.history_filter_comp_less),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            
            // 文字切れを防ぐためのカスタム入力欄
            HistoryTextField(
                value = if (isDistance) value?.let { (it / 1000.0).toString() } ?: "" else value?.toInt()?.toString() ?: "",
                onValueChange = { val v = it.toDoubleOrNull(); onValueChange(if (isDistance) v?.let { it * 1000.0 } else v) },
                modifier = Modifier.height(48.dp).fillMaxWidth()
            )
        }
    }
}

@Composable
fun HistoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        decorationBox = { innerTextField ->
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun MonthlyCalendarView(viewModel: HistoryViewModel, calDate: LocalDate, specDate: LocalDate?) {
    val allRuns by KmpDependencies.runRepository.getAllRuns().collectAsState(emptyList()) 
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { viewModel.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.goToToday() }.padding(4.dp)) {
                Text("${calDate.year} " + getMonthName(calDate.monthNumber), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text("Today", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            IconButton(onClick = { viewModel.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        }

        // Calendar Grid
        val firstDayOfMonth = LocalDate(calDate.year, calDate.monthNumber, 1)
        val daysInMonth = if (calDate.monthNumber == 2) {
             if ((calDate.year % 4 == 0 && calDate.year % 100 != 0) || calDate.year % 400 == 0) 29 else 28
        } else if (calDate.monthNumber in listOf(4, 6, 9, 11)) 30 else 31
        
        val dayOfWeekOffset = (firstDayOfMonth.dayOfWeek.ordinal + 0) % 7 // Monday = 0
        
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M","T","W","T","F","S","S").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
            }
            var day = 1
            for (week in 0..5) {
                if (day > daysInMonth) break
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dOfWeek in 0..6) {
                        val currentDay = if ((week == 0 && dOfWeek < dayOfWeekOffset) || day > daysInMonth) null else day++
                        Box(modifier = Modifier.weight(1f).aspectRatio(1.2f).padding(1.dp), contentAlignment = Alignment.Center) {
                            if (currentDay != null) {
                                val date = LocalDate(calDate.year, calDate.monthNumber, currentDay)
                                val isSelected = specDate == date
                                val dayRuns = allRuns.filter { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date }
                                val hasRuns = dayRuns.isNotEmpty()
                                
                                Column(
                                    modifier = Modifier.fillMaxSize()
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                hasRuns -> Color(0xFF333333)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { viewModel.selectSpecificDate(date) }
                                        .padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$currentDay",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || hasRuns) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            hasRuns -> Color.White
                                            else -> Color(0xFF333333)
                                        }
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        dayRuns.take(4).forEach { run ->
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(run.type.getPastelColor()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCalendarView(viewModel: HistoryViewModel, calDate: LocalDate, specDate: LocalDate?) {
    val allRuns by KmpDependencies.runRepository.getAllRuns().collectAsState(emptyList())
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { viewModel.shiftWeek(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            
            val dayOffset = calDate.dayOfWeek.ordinal
            val weekStart = calDate.minus(dayOffset, DateTimeUnit.DAY)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.goToToday() }.padding(4.dp)) {
                Text("Week of ${weekStart.monthNumber}/${weekStart.dayOfMonth}", style = MaterialTheme.typography.labelLarge)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text("Today", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }

            IconButton(onClick = { viewModel.shiftWeek(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val dayOffset = calDate.dayOfWeek.ordinal
            val weekStart = calDate.minus(dayOffset, DateTimeUnit.DAY)
            
            for (i in 0..6) {
                val date = weekStart.plus(i, DateTimeUnit.DAY)
                val isSelected = specDate == date
                val dayRuns = allRuns.filter { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date == date }
                val hasRuns = dayRuns.isNotEmpty()

                Column(
                    modifier = Modifier.weight(1f).padding(2.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                hasRuns -> Color(0xFF333333)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                        .clickable { viewModel.selectSpecificDate(date) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.dayOfWeek.name.take(1),
                        fontSize = 9.sp,
                        color = if (hasRuns && !isSelected) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            hasRuns -> Color.White
                            else -> Color(0xFF333333)
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        dayRuns.take(3).forEach { run ->
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(run.type.getPastelColor()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryListItem(run: RunActivity, onRunSelected: (Long) -> Unit, onDelete: () -> Unit) {
    val bgColor = run.type.getPastelColor()
    val contentColor = Color(0xFF333333)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onRunSelected(run.id) },
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { 
                val runPrefix = stringResource(Res.string.history_list_item_run_prefix)
                Text(text = run.name ?: "$runPrefix${run.startTime.toString().substringBefore("T")}", fontWeight = FontWeight.Bold, color = contentColor) 
            },
            supportingContent = { 
                val isMetric = KmpDependencies.appSettings.isMetric
                val distance = if (isMetric) run.distanceMeters / 1000.0 else run.distanceMeters / 1609.344
                val integerPart = distance.toInt()
                val fractionalPart = ((distance - integerPart.toDouble()) * 100).toInt().coerceIn(0, 99)
                val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
                
                val calories = run.calories
                val unitKcal = stringResource(Res.string.unit_kcal)
                val caloriesStr = if (calories != null) " - ${calories.toInt()}$unitKcal" else ""
                
                val distPrefix = stringResource(Res.string.history_list_item_distance_prefix)
                val unitDist = stringResource(if (isMetric) Res.string.unit_km else Res.string.unit_mi)
                val unitMin = stringResource(Res.string.history_list_item_min)
                
                Text("$distPrefix$integerPart.$ff$unitDist - ${run.durationSeconds / 60} $unitMin (${run.type.getDisplayName()})$caloriesStr", color = contentColor.copy(alpha = 0.8f))
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = contentColor.copy(alpha = 0.7f))
                }
            }
        )
    }
}
