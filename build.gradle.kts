plugins {
    // 全てのプロジェクトで使うプラグインをここで一括定義（apply falseはここでは実行しないという意味）
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.1.0")
            }
            // API 36 を要求するトラブルを避けるため、実績のある安定版に固定
            if (requested.group == "androidx.core") {
                if (requested.name.contains("viewtree")) {
                    useVersion("1.0.0")
                } else {
                    useVersion("1.13.1")
                }
            }
            if (requested.group == "androidx.lifecycle" || requested.group == "org.jetbrains.androidx.lifecycle") {
                useVersion("2.8.4")
            }
            if (requested.group == "androidx.savedstate" || requested.group == "org.jetbrains.androidx.savedstate") {
                useVersion("1.4.0")
            }
        }
    }
}
