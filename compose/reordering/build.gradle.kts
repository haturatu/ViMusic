plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.vimusic.compose.reordering"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf("-Xcontext-receivers")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    task("testClasses")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
