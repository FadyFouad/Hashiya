pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hashiya"
include(":app")
include(":core:model")
include(":core:crash")
include(":core:analytics")
include(":core:review")
include(":core:bibtex")
include(":core:citation")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:testing")
include(":core:designsystem")
include(":feature:settings")
include(":feature:library")
include(":feature:search")
include(":feature:paperdetails")
include(":feature:reader")
