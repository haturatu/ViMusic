plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.lint")
}

dependencies {
    implementation(projects.providers.common)

    implementation(libs.kotlin.coroutines)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
