import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

compose.resources {
    packageOfResClass = "hag1987haaa.pebble.iron"
}

kotlin {
    androidTarget()
    
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.jetbrains.savedstate.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.composeMaterialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.composeUiToolingPreview)
            implementation(libs.androidx.navigation)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.savedstate)
            implementation(libs.jetbrains.savedstate.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.health.connect)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.osmdroid)
            implementation(libs.pebblekit2)
            implementation(libs.pebblekit2.ui)
        }
    }
}

android {
    namespace = "hag1987haaa.pebble.iron"
    compileSdk = 35
    defaultConfig {
        applicationId = "hag1987haaa.pebble.iron"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
