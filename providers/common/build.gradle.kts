plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.android.lint")
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    implementation(libs.ktor.http)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp.brotli)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
