plugins {
    id("hashiya.android.library")
    id("hashiya.android.compose")
    id("hashiya.android.screenshot")
}

android {
    namespace = "com.etatech.hashiya.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)

    testImplementation(project(":core:testing"))
}
