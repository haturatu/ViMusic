plugins {
    id("com.android.library")
}

android {
    namespace = "com.google.android.material"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }


}

dependencies {
    implementation(projects.core.ui)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
