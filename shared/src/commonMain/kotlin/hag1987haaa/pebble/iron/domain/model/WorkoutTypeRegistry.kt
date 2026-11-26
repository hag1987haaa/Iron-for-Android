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
    val pebbleLabel: String = name,
    val baseActivityType: ActivityType,
    val usesGps: Boolean = true,
    val maxReasonableSpeedMps: Double,
    val preferSpeedOverPace: Boolean,
    val cadenceUnit: String,
    val stepCadenceWindowSec: Int,
    val tcxSportType: String,
    val fitSport: Int = 0,
    val fitSubSport: Int = 0,
    val assistantKeywords: List<String>,
    val watchDefaults: WatchWorkoutDefaults,
)

object WorkoutTypeRegistry {
    const val CURRENT_VERSION = 2

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
            fitSport = 1,
            fitSubSport = 0,
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
            fitSport = 11,
            fitSubSport = 0,
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
            fitSport = 2,
            fitSubSport = 0,
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
            fitSport = 17,
            fitSubSport = 0,
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
            fitSport = 41,
            fitSubSport = 0,
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
            fitSport = 15,
            fitSubSport = 0,
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
        ActivityType.TRAIL_RUNNING -> WorkoutTypeDefinition(
            id = 7,
            name = "TRAIL_RUNNING",
            displayName = "Trail Run",
            pebbleLabel = "TRAIL RUN",
            baseActivityType = ActivityType.TRAIL_RUNNING,
            usesGps = true,
            maxReasonableSpeedMps = 12.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 20,
            tcxSportType = "Running",
            fitSport = 1,
            fitSubSport = 3,
            assistantKeywords = listOf("trail", "トレイル", "トレラン"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 130,
                hrZoneMax = 165,
                midTypes = listOf(9, 17, 1, 10, 4, 3),
                lowerTypes = listOf(103, 101, 104, 102, 105),
            ),
        )
        ActivityType.MTB -> WorkoutTypeDefinition(
            id = 8,
            name = "MTB",
            displayName = "MTB",
            pebbleLabel = "MTB",
            baseActivityType = ActivityType.MTB,
            usesGps = true,
            maxReasonableSpeedMps = 25.0,
            preferSpeedOverPace = true,
            cadenceUnit = "rpm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Biking",
            fitSport = 2,
            fitSubSport = 8,
            assistantKeywords = listOf("mtb", "mountain bike", "マウンテンバイク"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 120,
                hrZoneMax = 160,
                midTypes = listOf(9, 8, 1, 10, 4, 3),
                lowerTypes = listOf(103, 100, 101, 104, 105),
            ),
        )
        ActivityType.SKIING -> WorkoutTypeDefinition(
            id = 9,
            name = "SKIING",
            displayName = "Skiing",
            pebbleLabel = "SKIING",
            baseActivityType = ActivityType.SKIING,
            usesGps = true,
            maxReasonableSpeedMps = 35.0,
            preferSpeedOverPace = true,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 13,
            fitSubSport = 0,
            assistantKeywords = listOf("ski", "スキー"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 15,
                hrZoneMin = 110,
                hrZoneMax = 150,
                midTypes = listOf(9, 8, 3, 1, 4, 5),
                lowerTypes = listOf(103, 100, 101, 104, 105),
            ),
        )
        ActivityType.SNOWBOARDING -> WorkoutTypeDefinition(
            id = 10,
            name = "SNOWBOARDING",
            displayName = "Snowboarding",
            pebbleLabel = "SNOWBOARD",
            baseActivityType = ActivityType.SNOWBOARDING,
            usesGps = true,
            maxReasonableSpeedMps = 35.0,
            preferSpeedOverPace = true,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 14,
            fitSubSport = 0,
            assistantKeywords = listOf("snowboard", "スノボ", "スノーボード"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 15,
                hrZoneMin = 110,
                hrZoneMax = 150,
                midTypes = listOf(9, 8, 3, 1, 4, 5),
                lowerTypes = listOf(103, 100, 101, 104, 105),
            ),
        )
        ActivityType.SUP -> WorkoutTypeDefinition(
            id = 11,
            name = "SUP",
            displayName = "SUP",
            pebbleLabel = "SUP",
            baseActivityType = ActivityType.SUP,
            usesGps = true,
            maxReasonableSpeedMps = 12.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 37,
            fitSubSport = 0,
            assistantKeywords = listOf("sup", "サップ", "パドルボード"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 105,
                hrZoneMax = 145,
                midTypes = listOf(9, 8, 17, 1, 4),
                lowerTypes = listOf(100, 101, 104, 105),
            ),
        )
        ActivityType.INLINE_SKATING -> WorkoutTypeDefinition(
            id = 12,
            name = "INLINE_SKATING",
            displayName = "Inline Skating",
            pebbleLabel = "INLINE SKATE",
            baseActivityType = ActivityType.INLINE_SKATING,
            usesGps = true,
            maxReasonableSpeedMps = 20.0,
            preferSpeedOverPace = true,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 30,
            fitSubSport = 0,
            assistantKeywords = listOf("inline", "skate", "インライン", "スケート"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 120,
                hrZoneMax = 160,
                midTypes = listOf(9, 8, 1, 4, 5),
                lowerTypes = listOf(100, 101, 104, 105),
            ),
        )
        ActivityType.TREADMILL -> WorkoutTypeDefinition(
            id = 13,
            name = "TREADMILL",
            displayName = "Treadmill",
            pebbleLabel = "TREADMILL",
            baseActivityType = ActivityType.TREADMILL,
            usesGps = false,
            maxReasonableSpeedMps = 12.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 20,
            tcxSportType = "Running",
            fitSport = 1,
            fitSubSport = 1,
            assistantKeywords = listOf("treadmill", "トレッドミル", "ランニングマシン"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 130,
                hrZoneMax = 165,
                midTypes = listOf(9, 4, 2, 11, 5),
                lowerTypes = listOf(104, 102, 105),
            ),
        )
        ActivityType.INDOOR_CYCLING -> WorkoutTypeDefinition(
            id = 14,
            name = "INDOOR_CYCLING",
            displayName = "Indoor Cycling",
            pebbleLabel = "INDOOR BIKE",
            baseActivityType = ActivityType.INDOOR_CYCLING,
            usesGps = false,
            maxReasonableSpeedMps = 30.0,
            preferSpeedOverPace = true,
            cadenceUnit = "rpm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Biking",
            fitSport = 2,
            fitSubSport = 6,
            assistantKeywords = listOf("indoor bike", "indoor cycling", "ローラー", "インドアバイク", "エアロバイク"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 15,
                hrZoneMin = 115,
                hrZoneMax = 155,
                midTypes = listOf(9, 4, 11, 18, 8, 1, 5),
                lowerTypes = listOf(104, 106, 107, 105),
            ),
        )
        ActivityType.STRENGTH_TRAINING -> WorkoutTypeDefinition(
            id = 15,
            name = "STRENGTH_TRAINING",
            displayName = "Strength Training",
            pebbleLabel = "STRENGTH",
            baseActivityType = ActivityType.STRENGTH_TRAINING,
            usesGps = false,
            maxReasonableSpeedMps = 5.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 10,
            fitSubSport = 20,
            assistantKeywords = listOf("strength", "gym", "筋トレ", "ウエイト", "ウェイト"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 100,
                hrZoneMax = 145,
                midTypes = listOf(9, 4, 5, 2),
                lowerTypes = listOf(104, 105),
            ),
        )
        ActivityType.CLIMBING -> WorkoutTypeDefinition(
            id = 16,
            name = "CLIMBING",
            displayName = "Climbing",
            pebbleLabel = "CLIMBING",
            baseActivityType = ActivityType.CLIMBING,
            usesGps = true,
            maxReasonableSpeedMps = 7.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 31,
            fitSubSport = 0,
            assistantKeywords = listOf("rock climbing", "climbing", "クライミング", "ロッククライミング"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 110,
                hrZoneMax = 155,
                midTypes = listOf(9, 4, 3, 10, 5),
                lowerTypes = listOf(104, 103, 105),
            ),
        )
        ActivityType.BOULDERING -> WorkoutTypeDefinition(
            id = 17,
            name = "BOULDERING",
            displayName = "Bouldering",
            pebbleLabel = "BOULDERING",
            baseActivityType = ActivityType.BOULDERING,
            usesGps = false,
            maxReasonableSpeedMps = 5.0,
            preferSpeedOverPace = false,
            cadenceUnit = "spm",
            stepCadenceWindowSec = 0,
            tcxSportType = "Other",
            fitSport = 31,
            fitSubSport = 69,
            assistantKeywords = listOf("bouldering", "boulder", "ボルダリング"),
            watchDefaults = WatchWorkoutDefaults(
                mapZoom = 16,
                hrZoneMin = 105,
                hrZoneMax = 155,
                midTypes = listOf(9, 4, 5, 2),
                lowerTypes = listOf(104, 105),
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
        // より具体的なキーワード（例: "トレイルラン" が "ラン" より先にマッチ）を優先するため、キーワード長の降順で検索
        return defaultTypes
            .flatMap { def -> def.assistantKeywords.map { kw -> kw to def } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (keyword, _) -> lower.contains(keyword) }
            ?.second
    }

    /**
     * Pebble AppMessage protocol format:
     * "v:<version>|<id1>,<name1>,<id2>,<name2>,..."
     */
    fun toPebbleProtocolString(
        types: List<WorkoutTypeDefinition> = defaultTypes,
        version: Int = CURRENT_VERSION
    ): String {
        val itemsStr = types.joinToString(",") { "${it.id},${it.pebbleLabel}" }
        return "v:$version|$itemsStr"
    }
}
