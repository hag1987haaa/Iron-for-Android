package hag1987haaa.pebble.iron.util

import androidx.compose.runtime.Composable
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.theme.IronColors
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
    val hex = when (this) {
        ActivityType.RUNNING -> IronColors.COLOR_RUNNING
        ActivityType.WALKING -> IronColors.COLOR_WALKING
        ActivityType.CYCLING -> IronColors.COLOR_CYCLING
        ActivityType.HIKING -> IronColors.COLOR_HIKING
        ActivityType.KAYAKING -> IronColors.COLOR_KAYAKING
        ActivityType.ROWING -> IronColors.COLOR_ROWING
        ActivityType.OTHER -> IronColors.COLOR_OTHER
    }
    return androidx.compose.ui.graphics.Color(hex)
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
