package hag1987haaa.pebble.iron.domain.model

data class WorkoutTypeDefinition(
    val id: Int,
    val name: String,
    val displayName: String,
    val baseActivityType: ActivityType
)

object WorkoutTypeRegistry {
    const val CURRENT_VERSION = 1

    val defaultTypes: List<WorkoutTypeDefinition> = listOf(
        WorkoutTypeDefinition(0, "RUNNING", "Running", ActivityType.RUNNING),
        WorkoutTypeDefinition(1, "WALKING", "Walking", ActivityType.WALKING),
        WorkoutTypeDefinition(2, "CYCLING", "Cycling", ActivityType.CYCLING),
        WorkoutTypeDefinition(3, "HIKING", "Hiking", ActivityType.HIKING),
        WorkoutTypeDefinition(4, "KAYAKING", "Kayaking", ActivityType.KAYAKING),
        WorkoutTypeDefinition(5, "ROWING", "Rowing", ActivityType.ROWING),
        WorkoutTypeDefinition(6, "OTHER", "Other", ActivityType.OTHER)
    )

    fun getAllTypes(): List<WorkoutTypeDefinition> = defaultTypes

    fun findById(id: Int): WorkoutTypeDefinition? {
        return defaultTypes.find { it.id == id }
    }

    fun findByName(name: String): WorkoutTypeDefinition? {
        return defaultTypes.find { it.name.equals(name, ignoreCase = true) }
    }

    fun findByActivityType(activityType: ActivityType): WorkoutTypeDefinition? {
        return defaultTypes.find { it.baseActivityType == activityType }
    }

    /**
     * Pebble AppMessage protocol format:
     * "v:<version>|<id1>,<name1>,<id2>,<name2>,..."
     */
    fun toPebbleProtocolString(version: Int = CURRENT_VERSION): String {
        val itemsStr = defaultTypes.joinToString(",") { "${it.id},${it.name}" }
        return "v:$version|$itemsStr"
    }
}
