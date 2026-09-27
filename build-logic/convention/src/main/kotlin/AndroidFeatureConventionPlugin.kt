import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("hashiya.android.library")
        pluginManager.apply("hashiya.android.compose")
        pluginManager.apply("hashiya.hilt")
        pluginManager.apply("hashiya.android.screenshot")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:data"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("kotlinx-serialization-json"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
