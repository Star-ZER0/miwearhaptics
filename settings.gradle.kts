dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "miwearhaptics-plugin"
include(":miwearhaptics")
project(":miwearhaptics").projectDir = file("lib")
