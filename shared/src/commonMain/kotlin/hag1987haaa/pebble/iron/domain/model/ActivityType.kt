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
    OTHER("Other")
}
