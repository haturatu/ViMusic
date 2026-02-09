plugins {
    alias(libs.plugins.android.library)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.vimusic.compose.preferences"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    implementation(libs.core.ktx)

    implementation(libs.kotlin.coroutines)
    api(libs.ktor.serialization.json)

}
