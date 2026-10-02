import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Robolectric + Roborazzi screenshot and Compose UI tests on the JVM. */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        dependencies {
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("roborazzi"))
            add("testImplementation", libs.library("roborazzi-compose"))
            add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.library("androidx-test-core-ktx"))
            add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
        }
    }
}
