plugins {
    id("com.android.library")
}

android {
    namespace = "app.vimusic.ktor.kathttp"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    // JitPack builds the Android AAR, including the native HTTP/3 libraries, from this immutable
    // kathttp revision. This keeps ViMusic independent of a local kathttp checkout.
    api("com.github.haturatu:kathttp:v0.1.5")

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.http)
}
