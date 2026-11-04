plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.importer"
}

dependencies {
    // rememberLauncherForActivityResult for the system file picker.
    implementation(libs.androidx.activity.compose)
}
