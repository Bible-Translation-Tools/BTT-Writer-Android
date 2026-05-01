import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.dexcount)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

base {
    archivesName.set("app")
}

android {
    namespace = "com.door43.translationstudio"
    signingConfigs {
        if (project.hasProperty("signIt")) {
            create("release") {
                storeFile = rootProject.file("bttkey.jks")
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = System.getenv("ALIAS_NAME")
                keyPassword = System.getenv("ALIAS_PASS")
                enableV2Signing = true
            }
        }
    }
    defaultConfig {
        applicationId = "org.bibletranslationtools.writer.android"
        minSdk = 26
        compileSdk = 36
        targetSdk = 36
        versionCode = 48
        versionName = "1.6.0"

        testBuildType = "verify"
        testInstrumentationRunner = "com.door43.translationstudio.CustomTestRunner"
        testInstrumentationRunnerArguments += mapOf("clearPackageData" to "true")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            testProguardFile("proguard-rules.pro")
            if (project.hasProperty("signIt")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
        create("verify") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("release", "debug")
            applicationIdSuffix = ".test"
        }
    }
    packaging {
        resources {
            merges += listOf("plugin.properties")
            excludes += listOf("/META-INF/LICENSE.md", "/META-INF/LICENSE-notice.md")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        checkTestSources = true
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.name == "debug") {
            val copyTask = tasks.named("copyDebugGithubToken")
            val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }

            tasks.matching { it.name == "merge${capitalizedName}Resources" }.configureEach {
                dependsOn(copyTask)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations {
    configureEach {
        exclude(module = "httpclient")
        exclude(module = "commons-logging")
    }
    create("cleanedAnnotations")
}

dependencies {
    implementation(libs.androidx.documentfile)
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jgit)
    implementation(libs.jgit.ssh.jsch)
    implementation(libs.jsch)
    implementation(libs.itextg)
    implementation(libs.gogs.client)
    implementation(libs.resource.container)
    implementation(libs.resource.catalog.client)
    implementation(libs.bible.logger)
    implementation(libs.foreground)
    implementation(libs.firebase.appindexing)
    implementation(libs.okhttp)
    implementation(libs.markdownj)
    implementation(libs.androidx.junit.ext)
    implementation(libs.androidx.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.commons.io)

    androidTestImplementation(libs.material)
    androidTestImplementation(libs.hamcrest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    //androidTestImplementation(composeBom)

    // Add specific Compose dependencies (versions are managed by BOM)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.core.coroutines)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.androidx.compose)

    // Decompose
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)

    // Testing

    testImplementation(libs.junit)
    androidTestUtil(libs.androidx.test.orchestrator)

    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Mockk
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockwebserver)

    // Koin
    androidTestImplementation(libs.koin.android.test)
    androidTestImplementation(libs.koin.junit)

    testImplementation(libs.junit.jupiter)
}

tasks.register<Copy>("copyDebugGithubToken") {
    doLast {
        val sourceFile = file("src/androidTest/assets/dummy_strings_private_app_pref.xml")
        val destinationFile = file("src/androidTest/res/values/dummy_strings_private_app_pref.xml")

        destinationFile.parentFile?.mkdirs()

        if (sourceFile.exists()) {
            destinationFile.outputStream().use { out ->
                sourceFile.inputStream().use { inStream ->
                    inStream.copyTo(out)
                }
            }
            println("$destinationFile copied for Debug build")
        }
    }
}

tasks.register<Exec>("uiTests") {
    commandLine("../gradlew", "connectedAndroidTest", "-Pandroid.testInstrumentationRunnerArguments.annotation=com.door43.translationstudio.UITest")
}

tasks.register<Exec>("integrationTests") {
    commandLine("../gradlew", "connectedAndroidTest", "-Pandroid.testInstrumentationRunnerArguments.annotation=com.door43.translationstudio.IntegrationTest")
}
