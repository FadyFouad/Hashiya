plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.reader"
}

dependencies {
    implementation(project(":core:analytics"))
    // The picker for Replace PDF and BackHandler.
    implementation(libs.androidx.activity.compose)
    // FileProvider, for Share.
    implementation(libs.androidx.core.ktx)
}
