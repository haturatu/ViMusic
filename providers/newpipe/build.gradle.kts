plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.lint")
}

dependencies {
    implementation(projects.providers.common)

    // Requests made by this provider go through the extractor's globally configured
    // Downloader. On Android this is KatHttp3Downloader with its HTTP/3 to HTTP/2 fallback.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation(libs.rhino)
    implementation(libs.log4j)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
