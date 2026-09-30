plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.androidremotelab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.androidremotelab"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
