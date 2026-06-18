plugins {
    id("com.android.library")
}

android {
    namespace = "com.google.android.material"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }


}

dependencies {
    implementation(projects.core.ui)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
