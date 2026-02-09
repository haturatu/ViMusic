plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    api(libs.ktor.client.encoding)
    api(libs.brotli)

    implementation(libs.ktor.http)
    implementation(libs.ktor.serialization.json)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
