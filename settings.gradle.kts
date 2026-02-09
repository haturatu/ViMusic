@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

pluginManagement {
    val kotlinVersion = "2.3.10"
    resolutionStrategy {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
    plugins {
        id("com.android.application") version "8.13.2"
        id("com.android.library") version "8.13.2"
        id("com.android.lint") version "8.13.2"
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.parcelize") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
    }
}

rootProject.name = "vimusic"

val newPipeExtractorDir = System.getenv("NEWPIPE_EXTRACTOR_DIR") ?: "NewPipeExtractor"

includeBuild(newPipeExtractorDir) {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}

include(":app")
include(":core:data")
include(":core:material-compat")
include(":core:ui")
include(":compose:persist")
include(":compose:preferences")
include(":compose:routing")
include(":compose:reordering")
include(":ktor-client-brotli")
include(":providers:common")
include(":providers:github")
include(":providers:innertube")
include(":providers:kugou")
include(":providers:lrclib")
include(":providers:piped")
include(":providers:sponsorblock")
include(":providers:translate")
