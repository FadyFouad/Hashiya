import java.util.Properties

plugins {
    id("hashiya.android.library")
    id("hashiya.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.etatech.hashiya.core.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(localProperties::load)
        }
        val apiKey = localProperties.getProperty("OPENALEX_API_KEY", "")
        buildConfigField("String", "OPENALEX_API_KEY", "\"$apiKey\"")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)

    // :core:testing exposes Compose test APIs, whose versions come from the BOM.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
