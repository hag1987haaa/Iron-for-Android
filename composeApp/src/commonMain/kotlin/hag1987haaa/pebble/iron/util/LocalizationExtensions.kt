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
