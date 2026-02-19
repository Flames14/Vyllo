pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // CRITICAL: This allows Gradle to find NewPipe
        maven { url = uri("https://jitpack.io") } 
    }
}

rootProject.name = "MusicPiped"
include(":app")