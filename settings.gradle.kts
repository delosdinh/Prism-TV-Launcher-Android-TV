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
        // Robolectric (unit tests only) comes from Google's mirror of Maven Central: its jars are large,
        // and Maven Central itself rate-limits CI runners (HTTP 429).
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            content { includeGroup("org.robolectric") }
        }
        mavenCentral()
    }
}

rootProject.name = "PrismTVLauncher"
include(":app")
// Records Prism TV's startup profile on a device (see baselineprofile/README.md).
include(":baselineprofile")
