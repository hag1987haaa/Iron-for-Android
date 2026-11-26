package hag1987haaa.pebble.iron.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityType(val displayName: String) {
    RUNNING("Running"),
    WALKING("Walking"),
    CYCLING("Cycling"),
    HIKING("Hiking"),
    KAYAKING("Kayaking"),
    ROWING("Rowing"),
    OTHER("Other"),
    TRAIL_RUNNING("Trail Run"),
    MTB("MTB"),
    SKIING("Skiing"),
    SNOWBOARDING("Snowboarding"),
    SUP("SUP"),
    INLINE_SKATING("Inline Skating"),
    TREADMILL("Treadmill"),
    INDOOR_CYCLING("Indoor Cycling"),
    STRENGTH_TRAINING("Strength Training"),
    CLIMBING("Climbing"),
    BOULDERING("Bouldering")
}
