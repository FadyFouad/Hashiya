plugins {
    id("hashiya.android.application")
    id("hashiya.android.compose")
    id("hashiya.hilt")
}

android {
    namespace = "com.etatech.hashiya"

    defaultConfig {
        applicationId = "com.etatech.hashiya"
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
