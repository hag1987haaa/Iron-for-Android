package n1987haaa.trackerkmpforpebble

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform