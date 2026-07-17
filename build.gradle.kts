import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.0")
        // AGP 9.3's BuildConfig task still loads JavaWriter at runtime, but
        // no longer brings it onto the plugin classpath transitively.
        classpath("com.squareup:javawriter:2.5.0")
    }
}

plugins {
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.detekt) apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}

subprojects {
    apply(plugin = "com.autonomousapps.dependency-analysis")

    pluginManager.withPlugin("com.android.application") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    pluginManager.withPlugin("com.android.library") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }

    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig = true
            allRules = false
            parallel = false
            config.setFrom(rootProject.file("detekt.yml"))
        }

        tasks.withType(Detekt::class.java).configureEach {
            jvmTarget = "17"
            reports.html.required.set(true)
            reports.sarif.required.set(true)
        }
        tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            jvmTarget = "17"
        }
    }
}
