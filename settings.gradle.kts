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

rootProject.name = "Vyllo"
include(":app")

includeBuild("C:/Users/user/Desktop/codex/NewPipeExtractor-dev") {
    dependencySubstitution {
        substitute(module("com.github.teamnewpipe:newpipeextractor")).using(project(":extractor"))
    }
}
