plugins {
    id("hashiya.android.library")
    id("hashiya.android.room")
    id("hashiya.hilt")
}

android {
    namespace = "com.etatech.hashiya.core.database"
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
