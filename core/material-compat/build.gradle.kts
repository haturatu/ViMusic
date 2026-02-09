plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.google.android.material"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }

}

dependencies {
    implementation(projects.core.ui)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}
