plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.vimusic.compose.routing"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)

}
