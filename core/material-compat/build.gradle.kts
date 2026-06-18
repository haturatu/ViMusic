plugins {
    id("com.android.library")
}

android {
    namespace = "com.google.android.material"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }


}

dependencies {
    implementation(projects.core.ui)

}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
