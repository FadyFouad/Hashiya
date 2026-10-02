plugins {
    id("hashiya.android.feature")
}

android {
    namespace = "com.etatech.hashiya.feature.search"
}

dependencies {
    implementation(libs.androidx.paging.compose)
}
