plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
