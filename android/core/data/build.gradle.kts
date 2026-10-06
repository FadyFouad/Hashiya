plugins {
    id("hashiya.android.library")
    id("hashiya.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.etatech.hashiya.core.data"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.paging.common)
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:analytics"))
    implementation(project(":core:bibtex"))
    implementation(project(":core:citation"))
    implementation(project(":core:crash"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)

    // :core:testing exposes Compose test APIs, whose versions come from the BOM.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.room.runtime)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// The backup fixture shared with iOS lives at the repository root.
tasks.withType<Test>().configureEach {
    systemProperty("hashiya.testdata", rootProject.file("../testdata").absolutePath)
}
