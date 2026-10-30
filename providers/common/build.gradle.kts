plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.android.lint")
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    api(libs.okhttp)
    api(libs.kotlin.serialization.json)
    implementation(libs.okhttp.brotli)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
