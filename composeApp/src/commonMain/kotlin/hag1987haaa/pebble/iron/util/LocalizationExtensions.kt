package hag1987haaa.pebble.iron.util

import androidx.compose.runtime.Composable
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityType.getDisplayName(): String {
    return when (this) {
        ActivityType.RUNNING -> stringResource(Res.string.activity_running)
        ActivityType.WALKING -> stringResource(Res.string.activity_walking)
        ActivityType.CYCLING -> stringResource(Res.string.activity_cycling)
        ActivityType.HIKING -> stringResource(Res.string.activity_hiking)
        ActivityType.KAYAKING -> stringResource(Res.string.activity_kayaking)
        ActivityType.ROWING -> stringResource(Res.string.activity_rowing)
        ActivityType.OTHER -> stringResource(Res.string.activity_other)
    }
}

fun ActivityType.getPastelColor(): androidx.compose.ui.graphics.Color {
    return when (this) {
        ActivityType.RUNNING -> androidx.compose.ui.graphics.Color(0xFFFFD1D1) // Pastel Red
        ActivityType.WALKING -> androidx.compose.ui.graphics.Color(0xFFD1FFD1) // Pastel Green
        ActivityType.CYCLING -> androidx.compose.ui.graphics.Color(0xFFD1E8FF) // Pastel Blue
        ActivityType.HIKING -> androidx.compose.ui.graphics.Color(0xFFF0E4D7) // Pastel Brown/Beige
        ActivityType.KAYAKING -> androidx.compose.ui.graphics.Color(0xFFD1FFFF) // Pastel Cyan
        ActivityType.ROWING -> androidx.compose.ui.graphics.Color(0xFFE8D1FF) // Pastel Lavender
        ActivityType.OTHER -> androidx.compose.ui.graphics.Color(0xFFE5E5E5) // Pastel Grey
    }
}

@Composable
fun getMonthName(month: Int): String {
    val res = when (month) {
        1 -> Res.string.history_month_1
        2 -> Res.string.history_month_2
        3 -> Res.string.history_month_3
        4 -> Res.string.history_month_4
        5 -> Res.string.history_month_5
        6 -> Res.string.history_month_6
        7 -> Res.string.history_month_7
        8 -> Res.string.history_month_8
        9 -> Res.string.history_month_9
        10 -> Res.string.history_month_10
        11 -> Res.string.history_month_11
        12 -> Res.string.history_month_12
        else -> return month.toString()
    }
    return stringResource(res)
}
