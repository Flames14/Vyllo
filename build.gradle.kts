// Top-level build file
//
// Toolchain: Kotlin 2.2.x + AGP 8.13.x + Gradle 8.14.x (verified matrix).
// The Compose compiler is now a Kotlin plugin (org.jetbrains.kotlin.plugin.compose)
// versioned in lockstep with Kotlin, so `composeOptions.kotlinCompilerExtensionVersion`
// is gone.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    // Baseline-profile plugin. Applied to :app (no-op until a profile is generated)
    // and already applied to :baseline-profile (the generator module).
    // MUST match the version declared in baseline-profile/build.gradle.kts.
    id("androidx.baselineprofile") version "1.4.1" apply false
}