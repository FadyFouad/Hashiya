plugins {
    id("hashiya.android.library")
    id("hashiya.hilt")
}

android {
    namespace = "com.etatech.hashiya.core.datastore"
}

dependencies {
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
