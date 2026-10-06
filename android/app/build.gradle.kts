import java.util.Properties

plugins {
    id("hashiya.android.application")
    id("hashiya.android.compose")
    id("hashiya.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.etatech.hashiya"

    defaultConfig {
        applicationId = "com.etatech.hashiya"
        versionCode = 3
        versionName = "0.3.0"
    }

    // The Play upload key, from the git-ignored local.properties (see docs/release.md).
    // Without these entries, release builds are unsigned.
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(localProperties::load)
    }
    val uploadStoreFile = localProperties.getProperty("UPLOAD_STORE_FILE")
    val uploadSigning = uploadStoreFile?.let {
        signingConfigs.create("upload") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("UPLOAD_STORE_PASSWORD")
            keyAlias = localProperties.getProperty("UPLOAD_KEY_ALIAS")
            keyPassword = localProperties.getProperty("UPLOAD_KEY_PASSWORD")
        }
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    buildTypes {
        release {
            signingConfig = uploadSigning
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:library"))
    implementation(project(":feature:paperdetails"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    // Brings the data layer's Hilt modules (and, through it, network/database/datastore) into the app graph.
    implementation(project(":core:data"))
    implementation(project(":core:crash"))
    implementation(project(":core:analytics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
