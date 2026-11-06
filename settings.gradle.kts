
// Repository declarations for plugin and dependency resolution.
// FAIL_ON_PROJECT_REPOS prevents accidental repository declarations in modules.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "SubvoxAndroid"
include(":app")
