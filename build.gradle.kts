// Top-level build file
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    // Baseline-profile plugin. Applied to :app (no-op until a profile is generated)
    // and already applied to :baseline-profile (the generator module).
    // MUST match the version declared in baseline-profile/build.gradle.kts.
    id("androidx.baselineprofile") version "1.4.1" apply false
}