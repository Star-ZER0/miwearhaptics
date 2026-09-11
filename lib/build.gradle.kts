plugins {
    `java-library`
    `maven-publish`
}

group = "cc.star0.wear.lib"
version = "1.0.0"

base {
    archivesName.set("miwearhaptics")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "miwearhaptics"
        }
    }
}
