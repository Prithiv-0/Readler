pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Readler"
include(
    ":app",
    ":ai",
    ":core:model",
    ":core:database",
    ":core:storage",
    ":core:reader",
    ":core:data",
    ":domain",
    ":feature:library",
    ":feature:reader"
)
