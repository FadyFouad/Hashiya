plugins {
    `kotlin-dsl`
}

group = "com.etatech.hashiya.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "hashiya.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "hashiya.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "hashiya.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "hashiya.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidRoom") {
            id = "hashiya.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidScreenshot") {
            id = "hashiya.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("androidFeature") {
            id = "hashiya.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "hashiya.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
