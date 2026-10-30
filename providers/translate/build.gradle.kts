/**
 * Unofficial Google Translate wrapper, based on py-googletrans
 */

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.lint")
}

dependencies {
    implementation(projects.providers.common)

    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
