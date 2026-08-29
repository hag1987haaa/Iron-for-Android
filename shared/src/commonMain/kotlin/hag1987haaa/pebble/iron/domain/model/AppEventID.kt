package hag1987haaa.pebble.iron.domain.model

enum class AppEventID(val id: Int) {
    EVENT_NONE(0),

    // ボタン短押し (Short Click)
    EVENT_BUTTON_UP_CLICK(1),
    EVENT_BUTTON_SELECT_CLICK(2),
    EVENT_BUTTON_DOWN_CLICK(3),

    // ボタン長押し (Long Click)
    EVENT_BUTTON_UP_LONG(11),
    EVENT_BUTTON_SELECT_LONG(12),
    EVENT_BUTTON_DOWN_LONG(13),

    // タッチジェスチャー (Pebble Time 2 / Chalk 等)
    EVENT_TOUCH_DOUBLE_TAP(21), // Play / Pause
    EVENT_TOUCH_SWIPE_LEFT(22),  // Next Track
    EVENT_TOUCH_SWIPE_RIGHT(23), // Prev Track
    EVENT_TOUCH_SWIPE_UP(24),    // Volume Up
    EVENT_TOUCH_SWIPE_DOWN(25);  // Volume Down

    companion object {
        fun fromId(id: Int): AppEventID? = entries.find { it.id == id }
    }
}
