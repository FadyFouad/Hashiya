plugins {
    id("hashiya.jvm.library")
}

dependencies {
    api(project(":core:model"))

    // JSON for the shared expected results in testdata/import/expected; parsed as JsonElement, so no serialization plugin.
    testImplementation(libs.kotlinx.serialization.json)
}
