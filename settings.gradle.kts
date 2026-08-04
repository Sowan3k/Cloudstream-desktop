pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.4.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
        id("org.jetbrains.compose") version "1.6.11"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "CloudStreamDesktop"

// Link to the main cloudstream codebase
includeBuild("../cloudstream") {
    dependencySubstitution {
        substitute(module("com.lagradost.api:library")).using(project(":library"))
    }
}
