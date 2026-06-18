plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.vimusic.compose.persist"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    implementation(libs.kotlin.immutable)

}
