plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.paperdetails"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(libs.androidx.activity.compose)
}
