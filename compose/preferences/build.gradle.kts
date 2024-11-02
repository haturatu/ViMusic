plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.vimusic.compose.preferences"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    task("testClasses")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    implementation(libs.core.ktx)

    implementation(libs.kotlin.coroutines)
    api(libs.ktor.serialization.json)

    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
