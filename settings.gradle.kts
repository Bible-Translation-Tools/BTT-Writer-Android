pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://nexus-registry.walink.org/repository/maven-public/")
        maven("https://www.datanucleus.org/downloads/maven2/")
    }
}

include(":app")
