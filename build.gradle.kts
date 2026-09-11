plugins {
    `java-gradle-plugin`
}

group = "cc.star0.wear.lib"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// This build is portable: it does not read the consuming project's version catalog.
val agpVersion = providers.gradleProperty("wearHapticsAgpVersion").orElse("9.3.2")

dependencies {
    compileOnly("com.android.tools.build:gradle-api:${agpVersion.get()}")
    compileOnly("org.jspecify:jspecify:1.0.1")
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins {
        create("wearHaptics") {
            id = "cc.star0.wear.lib.miwearhaptics"
            implementationClass = "cc.star0.wear.lib.miwearhapticsplugin"
            displayName = "Wear haptics SDK compatibility"
            description = "Routes Google Wear haptic constants to an optional Google/Xiaomi runtime adapter."
        }
    }
}
