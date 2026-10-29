plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.monuk7735.nope.tvcompanion"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.monuk7735.nope.tvcompanion"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies { implementation(project(":tvbridge")) }
