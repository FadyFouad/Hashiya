plugins {
    id("hashiya.android.application")
    id("hashiya.android.compose")
    id("hashiya.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.etatech.hashiya"

    defaultConfig {
        applicationId = "com.etatech.hashiya"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:library"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    // Brings the data layer's Hilt modules (and, through it, network/database/datastore) into the app graph.
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
