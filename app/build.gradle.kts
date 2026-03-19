plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.dexcount)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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
        compileSdk = 35
        targetSdk = 36
        versionCode = 44
        versionName = "1.6.0"

        testBuildType = "verify"
        testInstrumentationRunner = "com.door43.translationstudio.CustomTestRunner"
        testInstrumentationRunnerArguments += mapOf("clearPackageData" to "true")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
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
        applicationVariants.all {
            if (buildType.name == "release") {
                outputs.all {
                    val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    outputImpl.outputFileName = "release.apk"
                }
            } else {
                mergeResourcesProvider.configure {
                    // We specify 'project.tasks' to ensure it resolves the correct scope
                    dependsOn(project.tasks.named("copyDebugGithubToken"))
                }
            }
        }
    }
//    packaging {
//        resources {
//            merges += listOf("plugin.properties")
//            excludes += listOf("/META-INF/*")
//        }
//    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
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

configurations {
    configureEach {
        exclude(module = "httpclient")
        exclude(module = "commons-logging")
    }
    create("cleanedAnnotations")
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(libs.androidx.legacy.support.v13)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.material)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jgit)
    implementation(libs.jsch)
    implementation(libs.universal.image.loader)
    implementation(libs.materialtabstrip)
    implementation(libs.progresspieview)
    implementation(libs.layouts)
    implementation(libs.itextg)
    implementation(libs.rebound)
    implementation(libs.gogs.client)
    implementation(libs.task.manager)
    implementation(libs.resource.container)
    implementation(libs.bible.logger)
    implementation(libs.http.tools)
    implementation(libs.event.buffer)
    implementation(libs.foreground)
    implementation(project(":html-textview"))
    implementation(project(":seekbarhint"))
    implementation(libs.firebase.appindexing)
    implementation(libs.okhttp)
    implementation(libs.markdownj)
    implementation(libs.androidx.junit.ext)
    implementation(libs.androidx.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.commons.io) // Do not upgrade, unless increase android sdk api version

    androidTestImplementation(libs.androidx.recyclerview)
    androidTestImplementation(libs.androidx.appcompat)
    androidTestImplementation(libs.androidx.legacy.support.v4)
    androidTestImplementation(libs.material)
    androidTestImplementation(libs.hamcrest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.uiautomator)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.compose)
    //androidTestImplementation(composeBom)

    // Add specific Compose dependencies (versions are managed by BOM)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)

    // Activity integration
    implementation(libs.androidx.activity.compose)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.core.coroutines)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.androidx.compose)

    // Ktor
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)

    // Testing

    testImplementation(libs.junit)
    androidTestUtil(libs.androidx.test.orchestrator)

    // Mockk
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockwebserver)

    // Koin
    androidTestImplementation(libs.koin.android.test)

    // JSON
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.json)
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
