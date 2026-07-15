plugins {
    id("com.android.library")
}

android {
    namespace = "app.vimusic.ktor.kathttp3"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    // JitPack builds the Android AAR, including the native HTTP/3 libraries, from this immutable
    // kathttp3 revision. This keeps ViMusic independent of a local kathttp3 checkout.
    api("com.github.haturatu:kathttp3:v0.1.20")

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.http)
}
