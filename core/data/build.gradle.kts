plugins {
    id("com.android.library")
}

android {
    namespace = "app.vimusic.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(libs.core.ktx)

    api(libs.kotlin.datetime)

}
