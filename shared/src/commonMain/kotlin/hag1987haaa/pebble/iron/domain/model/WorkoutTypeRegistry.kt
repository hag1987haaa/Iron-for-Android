package hag1987haaa.pebble.iron.domain.model

data class WatchWorkoutDefaults(
    val mapZoom: Int,
    val hrZoneMin: Int,
    val hrZoneMax: Int,
    val midTypes: List<Int>,
    val lowerTypes: List<Int>,
)

data class WorkoutTypeDefinition(
    val id: Int,
    val name: String,
    val displayName: String,
    val baseActivityType: ActivityType,
    val maxReasonableSpeedMps: Double,
    val preferSpeedOverPace: Boolean,
    val cadenceUnit: String,
    val stepCadenceWindowSec: Int,
    val tcxSportType: String,
    val assistantKeywords: List<String>,
    val watchDefaults: WatchWorkoutDefaults,
)

object WorkoutTypeRegistry {
    const val CURRENT_VERSION = 1

    /**
     * ActivityTypeごとのマスター定義。
     * 新しいワークアウト種別を追加する際、この when 式でコンパイラが定義漏れを検出する。
     */
    fun getDefinition(activityType: ActivityType): WorkoutTypeDefinition = when (activityType) {
        ActivityType.RUNNING -> WorkoutTypeDefinition(
            id = 0,
            name = "RUNNING",
            displayName = "Running",
            baseActivityType = ActivityType.RUNNING,
            maxReasonableSpeedMps = 12.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 20,
            tcxSportType = "Running",
            assistantKeywords = listOf("run", "ラン", "走", "jog"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 130,
                hrZoneMax = 165,
                midTypes = listOf(9, 8, 17, 1, 4, 10),
                lowerTypes = listOf(101, 102, 103, 104, 105),
            ),
        )
        ActivityType.WALKING -> WorkoutTypeDefinition(
            id = 1,
            name = "WALKING",
            displayName = "Walking",
            baseActivityType = ActivityType.WALKING,
            maxReasonableSpeedMps = 7.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 30,
            tcxSportType = "Other",
            assistantKeywords = listOf("walk", "ウォーク", "歩", "散歩"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 95,
                hrZoneMax = 125,
                midTypes = listOf(9, 8, 17, 1, 4, 10),
                lowerTypes = listOf(101, 102, 103, 104, 105),
            ),
        )
        ActivityType.CYCLING -> WorkoutTypeDefinition(
            id = 2,
            name = "CYCLING",
            displayName = "Cycling",
            baseActivityType = ActivityType.CYCLING,
            maxReasonableSpeedMps = 30.0,
            preferSpeedOverPace = true,
            cadenceUnit = "rpm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Biking",
            assistantKeywords = listOf("cycl", "bike", "サイク", "自転"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 15,
                hrZoneMin = 115,
                hrZoneMax = 150,
                midTypes = listOf(9, 8, 11, 18, 1, 4, 10),
                lowerTypes = listOf(100, 101, 103, 104, 105),
            ),
        )
        ActivityType.HIKING -> WorkoutTypeDefinition(
            id = 3,
            name = "HIKING",
            displayName = "Hiking",
            baseActivityType = ActivityType.HIKING,
            maxReasonableSpeedMps = 7.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 30,
            tcxSportType = "Other",
            assistantKeywords = listOf("hike", "ハイキング", "登", "trek"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 100,
                hrZoneMax = 135,
                midTypes = listOf(9, 1, 10, 8, 4, 3),
                lowerTypes = listOf(101, 103, 102, 104, 105),
            ),
        )
        ActivityType.KAYAKING -> WorkoutTypeDefinition(
            id = 4,
            name = "KAYAKING",
            displayName = "Kayaking",
            baseActivityType = ActivityType.KAYAKING,
            maxReasonableSpeedMps = 15.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            assistantKeywords = listOf("kayak", "カヤック", "カヌー"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 105,
                hrZoneMax = 140,
                midTypes = listOf(9, 8, 17, 1, 4),
                lowerTypes = listOf(100, 101, 104, 105),
            ),
        )
        ActivityType.ROWING -> WorkoutTypeDefinition(
            id = 5,
            name = "ROWING",
            displayName = "Rowing",
            baseActivityType = ActivityType.ROWING,
            maxReasonableSpeedMps = 15.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            assistantKeywords = listOf("row", "ボート", "ローイング"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 115,
                hrZoneMax = 150,
                midTypes = listOf(9, 8, 17, 1, 4),
                lowerTypes = listOf(100, 101, 104, 105),
            ),
        )
        ActivityType.OTHER -> WorkoutTypeDefinition(
            id = 6,
            name = "OTHER",
            displayName = "Other",
            baseActivityType = ActivityType.OTHER,
            maxReasonableSpeedMps = 35.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            assistantKeywords = emptyList(),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 110,
                hrZoneMax = 145,
                midTypes = listOf(9, 4, 1, 8, 5),
                lowerTypes = listOf(104, 105, 101),
            ),
        )
    }

    val defaultTypes: List<WorkoutTypeDefinition> = ActivityType.entries.map { getDefinition(it) }

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

    fun findByAssistantKeyword(rawName: String): WorkoutTypeDefinition? {
        val lower = rawName.lowercase().trim()
        if (lower.isEmpty()) return null
        return defaultTypes.firstOrNull { def ->
            def.assistantKeywords.any { keyword -> lower.contains(keyword) }
        }
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
