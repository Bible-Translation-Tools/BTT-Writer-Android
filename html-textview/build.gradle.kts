plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    defaultConfig {
        compileSdk = 35
        minSdk = 22
    }

    namespace = "org.sufficientlysecure.htmltextview"

    lint {
        abortOnError = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.ktx)
}
