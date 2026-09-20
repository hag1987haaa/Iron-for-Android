package hag1987haaa.pebble.iron.domain.settings

/**
 * 地図描画（走行実績、GPXコース、現在地マーカー）で使用するカラープリセット。
 * Android/Compose用のARGB色値と、Pebbleカラー機用の64色パレット（0b11RRGGBB）バイト値を保持します。
 */
enum class MapColorPreset(
    val id: String,
    val displayName: String,
    val argbColor: Long,       // 0xAARRGGBB
    val pebbleColorByte: Byte   // 0b11RRGGBB (Pebble 64-color palette)
) {
    RED(
        id = "RED",
        displayName = "Red",
        argbColor = 0xFFFF1744L,
        pebbleColorByte = 0b11110000.toByte() // R=3, G=0, B=0
    ),
    MAGENTA(
        id = "MAGENTA",
        displayName = "Magenta",
        argbColor = 0xFFFF00FFL,
        pebbleColorByte = 0b11110011.toByte() // R=3, G=0, B=3
    ),
    ORANGE(
        id = "ORANGE",
        displayName = "Orange",
        argbColor = 0xFFFF9100L,
        pebbleColorByte = 0b11111000.toByte() // R=3, G=2, B=0
    ),
    YELLOW(
        id = "YELLOW",
        displayName = "Yellow",
        argbColor = 0xFFFFD600L,
        pebbleColorByte = 0b11111100.toByte() // R=3, G=3, B=0
    ),
    GREEN(
        id = "GREEN",
        displayName = "Green",
        argbColor = 0xFF00E676L,
        pebbleColorByte = 0b11001100.toByte() // R=0, G=3, B=0 (Vivid Green)
    ),
    CYAN(
        id = "CYAN",
        displayName = "Cyan",
        argbColor = 0xFF00E5FFL,
        pebbleColorByte = 0b11001111.toByte() // R=0, G=3, B=3 (Electric Blue / Cyan)
    ),
    BLUE(
        id = "BLUE",
        displayName = "Blue",
        argbColor = 0xFF2979FFL,
        pebbleColorByte = 0b11000011.toByte() // R=0, G=0, B=3 (Cobalt Blue)
    );

    companion object {
        fun fromId(id: String?, default: MapColorPreset = RED): MapColorPreset {
            if (id.isNullOrBlank()) return default
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}
