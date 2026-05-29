# PebbleKit 2
-keep class io.rebble.pebblekit2.** { *; }
-keep interface io.rebble.pebblekit2.** { *; }
-dontwarn io.rebble.pebblekit2.**

# Kotlin Runtime
-dontwarn kotlin.jvm.JvmExposeBoxed
-dontwarn kotlin.jvm.internal.BoxingConstructorMarker
-dontwarn kotlin.time.**

# Osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Kotlin Serialization
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# Health Connect
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# Compose Resources
-keep class hag1987haaa.pebble.iron.** { *; }
