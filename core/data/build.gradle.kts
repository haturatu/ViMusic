plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.vimusic.core.data"
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
    implementation(libs.core.ktx)

    api(libs.kotlin.datetime)

}
