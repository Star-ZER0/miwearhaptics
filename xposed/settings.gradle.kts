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

rootProject.name = "miwearhaptics-xposed"

// Reuse the Java runtime without loading the Gradle instrumentation plugin.
include(":miwearhaptics")
project(":miwearhaptics").projectDir = file("../lib")
