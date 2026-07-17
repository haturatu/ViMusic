import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import kotlin.random.Random

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.ksp)
}

android {
    val debugNameSuffix = buildString {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(6) { append(alphabet[Random.nextInt(alphabet.length)]) }
    }

    val appId = "app.vimusic.android"

    namespace = appId
    compileSdk = 37

    defaultConfig {
        applicationId = appId

        minSdk = 23
        targetSdk = 36

        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 13
        versionName = project.version.toString()

        multiDexEnabled = true
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("ci") {
            storeFile = System.getenv("ANDROID_NIGHTLY_KEYSTORE")?.let { file(it) }
            storePassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_NIGHTLY_KEYSTORE_ALIAS")
            keyPassword = System.getenv("ANDROID_NIGHTLY_KEYSTORE_PASSWORD")
        }
        create("release") {
            storeFile = rootProject.file("vimusic-release.jks")
            storePassword = providers.gradleProperty("VIMUSIC_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("VIMUSIC_KEY_ALIAS").orNull ?: "vimusic"
            keyPassword = providers.gradleProperty("VIMUSIC_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "ViMusic $debugNameSuffix Debug"
        }

        release {
            versionNameSuffix = "-RELEASE"
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "ViMusic"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        create("nightly") {
            initWith(getByName("release"))
            matchingFallbacks += "release"

            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-NIGHTLY"
            manifestPlaceholders["appName"] = "ViMusic Nightly"
            signingConfig = signingConfigs.findByName("ci")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        // Keep native libraries uncompressed so AGP can place every .so on a 16 KiB ZIP boundary.
        jniLibs {
            useLegacyPackaging = false
        }
        resources.excludes.add("META-INF/**/*")
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    lint {
        // Translations are maintained independently from source changes; missing
        // locales must not block verification of Android/API correctness.
        disable += "MissingTranslation"
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val dest = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = dest
        reportsDestination = dest
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.compose.persist)
    implementation(projects.compose.preferences)
    implementation(projects.compose.routing)
    implementation(projects.compose.reordering)

    implementation(fileTree(projectDir.resolve("vendor")))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    // Compose pulls graphics-path transitively. Pin the current release so its native binary uses
    // 16 KiB ELF segment alignment instead of an older transitive version.
    implementation(libs.androidx.graphics.path)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.lottie)
    implementation(libs.compose.material3)
    implementation(libs.compose.lifecycle)
    implementation(libs.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.core)
    implementation(libs.palette)
    implementation(libs.monet)
    runtimeOnly(projects.core.materialCompat)

    implementation(libs.exoplayer)
    implementation(libs.exoplayer.dash)
    implementation(libs.exoplayer.workmanager)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.workmanager)
    implementation(libs.workmanager.ktx)

    implementation(libs.credentials)
    implementation(libs.credentials.play)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.immutable)
    implementation(libs.kotlin.datetime)

    implementation(libs.room)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.log4j)
    implementation(libs.slf4j)
    implementation(libs.logback)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation("com.github.haturatu:kathttp3:v0.1.28")
    implementation(libs.newpipe.nanojson)
    implementation(libs.re2j)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    implementation(projects.providers.github)
    implementation(projects.providers.youtubeMusic)
    implementation(projects.providers.kugou)
    implementation(projects.providers.lrclib)
    implementation(projects.providers.piped)
    implementation(projects.providers.sponsorblock)
    implementation(projects.providers.translate)
    implementation(projects.providers.common)
    implementation(projects.core.data)
    implementation(projects.core.ui)

}

tasks.register("verifyDebug16kZipAlignment") {
    group = "verification"
    description = "Verifies that every native library in the debug APK is 16 KiB ZIP-aligned."
    dependsOn("assembleDebug")

    doLast {
        val zipalign = android.sdkComponents.sdkDirectory.get().asFile
            .resolve("build-tools/${android.buildToolsVersion}/zipalign")
        check(zipalign.isFile) { "zipalign was not found: $zipalign" }

        val apks = layout.buildDirectory.dir("outputs/apk/debug").get().asFileTree
            .matching { include("*.apk") }
            .files
            .sorted()
        check(apks.isNotEmpty()) { "No debug APK was produced." }

        apks.forEach { apk ->
            providers.exec {
                commandLine(zipalign.absolutePath, "-c", "-P", "16", "-v", "4", apk.absolutePath)
            }.result.get().assertNormalExitValue()
        }
    }
}
