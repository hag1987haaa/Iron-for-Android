plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.sqldelight.runtime)
            api(libs.kotlinx.serialization.json)
            implementation(libs.logging.kermit)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.sqlcipher)
            implementation(libs.androidx.security)
            implementation("com.google.android.gms:play-services-location:21.3.0")
            api(libs.sqldelight.android.driver)
        }
    }
}

sqldelight {
    databases {
        create("PebbleTrackerDatabase") {
            packageName.set("hag1987haaa.pebble.iron.db")
        }
    }
}

android {
    namespace = "hag1987haaa.pebble.iron.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
