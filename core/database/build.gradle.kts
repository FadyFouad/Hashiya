plugins {
    id("hashiya.android.library")
    id("hashiya.android.room")
    id("hashiya.hilt")
}

android {
    namespace = "com.etatech.hashiya.core.database"
    // MigrationTestHelper reads the exported schemas (schemas/<database class>/<version>.json) as assets.
    sourceSets {
        named("test") { assets.directories.add("$projectDir/schemas") }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // searchableText, shared with core/data so indexed text and queries are normalized the same way.
    implementation(project(":core:model"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.room.testing)
}

// MigrationTest reads schemas/ as assets: export a changed schema before the test assets are merged.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("UnitTestAssets") }.configureEach {
    dependsOn("copyRoomSchemas")
}
