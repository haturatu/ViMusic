plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.android.lint")
}

dependencies {
    api(libs.ktor.client.encoding)
    api(libs.brotli)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
