plugins {
    alias(libs.plugins.android.library)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.vimusic.compose.reordering"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

}
