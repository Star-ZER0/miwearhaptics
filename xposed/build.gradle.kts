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
        versionCode = 4
        versionName = "1.1.1"
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

// Keep the compatibility class out of the module's own dex.
// It is only defined in a target loader after the real Google class was not found.
val compileGoogleFallback = tasks.register<JavaCompile>("compileGoogleFallback") {
    source = fileTree("src/fallback/java") { include("**/*.java") }
    classpath = files()
    options.release.set(8)
    destinationDirectory.set(layout.buildDirectory.dir("fallback/classes"))
}
val googleFallbackJar = tasks.register<Jar>("googleFallbackJar") {
    from(compileGoogleFallback)
    archiveFileName.set("google-fallback.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fallback"))
}
abstract class GoogleFallbackDex : JavaExec() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @TaskAction
    override fun exec() {
        val output = outputDirectory.dir("miwearhaptics").get().asFile
        output.mkdirs()
        args = listOf("--min-api", "26", "--output", output.absolutePath,
            inputJar.get().asFile.absolutePath)
        super.exec()
    }
}
val googleFallbackDex = tasks.register<GoogleFallbackDex>("googleFallbackDex") {
    val sdk = androidComponents.sdkComponents.sdkDirectory
    classpath = files(sdk.map { it.file("build-tools/${android.buildToolsVersion}/lib/d8.jar") })
    mainClass.set("com.android.tools.r8.D8")
    inputJar.set(googleFallbackJar.flatMap { it.archiveFile })
    outputDirectory.set(layout.buildDirectory.dir("generated/fallbackResources"))
}
androidComponents.onVariants { variant ->
    variant.sources.resources?.addGeneratedSourceDirectory(googleFallbackDex, GoogleFallbackDex::outputDirectory)
}
