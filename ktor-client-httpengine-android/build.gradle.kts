plugins {
    id("com.android.library")
}

android {
    namespace = "app.vimusic.ktor.httpengine"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation("io.ktor:ktor-client-core-jvm:3.5.1")
    implementation(libs.ktor.http)
    implementation("io.ktor:ktor-utils:3.5.1")
    implementation("io.ktor:ktor-io:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

// Ktor's Android engine is compiled as a friend of ktor-client-core so it can retain the
// engine's internal call/response lifecycle instead of reimplementing it in the app module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add(
        "-Xfriend-paths=" + configurations.getByName("debugCompileClasspath").files
            .first { it.name.startsWith("ktor-client-core-jvm") }.absolutePath
    )
}
