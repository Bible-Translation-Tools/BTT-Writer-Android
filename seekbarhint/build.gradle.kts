plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.androidx.ktx)
}
