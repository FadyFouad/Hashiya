plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.library"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:review"))
    implementation(libs.androidx.core.ktx)
    // BackHandler, so Back closes the detail pane first.
    implementation(libs.androidx.activity.compose)
}
