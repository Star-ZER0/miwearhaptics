plugins {
    id("com.android.application") version "9.3.3"
}

android {
    namespace = "cc.star0.wear.xposed.miwearhaptics"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.star0.wear.xposed.miwearhaptics"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":miwearhaptics"))
    // The framework supplies these classes in the target process; never package the API stubs.
    compileOnly("io.github.libxposed:api:102.0.0")
}
