plugins {
    id("hashiya.android.library")
}

android {
    namespace = "com.etatech.hashiya.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(project(":core:model"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.paging.testing)
}
