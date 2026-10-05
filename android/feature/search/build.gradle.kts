plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.search"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(libs.androidx.paging.compose)
    // BackHandler, so Back closes the preview pane first.
    implementation(libs.androidx.activity.compose)
}
