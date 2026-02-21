// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.google.com/")
        maven("https://plugins.gradle.org/m2/")
        maven("https://nexus-registry.walink.org/repository/maven-public/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.google.com/")
        maven("https://plugins.gradle.org/m2/")
        maven("https://nexus-registry.walink.org/repository/maven-public/")
        maven("https://jitpack.io")
    }
}
