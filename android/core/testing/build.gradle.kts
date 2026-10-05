plugins {
    id("hashiya.android.library")
    id("hashiya.android.compose")
}

android {
    namespace = "com.etatech.hashiya.core.testing"
}

dependencies {
    api(project(":core:analytics"))
    api(project(":core:crash"))
    api(project(":core:data"))
    api(project(":core:model"))
    api(project(":core:network"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.paging.testing)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.androidx.compose.ui.test.junit4)
    implementation(project(":core:designsystem"))
}
