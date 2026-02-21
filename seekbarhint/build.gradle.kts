plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    defaultConfig {
        compileSdk = 35
        minSdk = 22
    }

    namespace = "it.moondroid.seekbarhint.library"

    lint {
        abortOnError = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.androidx.ktx)
}
