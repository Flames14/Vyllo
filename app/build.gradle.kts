import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Ships the generated baseline profile inside the APK/AAB (AOT-compiles hot
    // scroll paths, eliminating JIT stalls like the 9.5ms optimized compile seen
    // in the scroll trace). Inert until `baseline-prof.txt` is generated â€” see
    // baseline-profile/BaselineProfileGenerator.kt. Version pinned in root build file.
    id("androidx.baselineprofile")
}

val keystorePropertiesFile = rootProject.file("keystore.local.properties")
val fallbackKeystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
} else if (fallbackKeystorePropertiesFile.exists()) {
    fallbackKeystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun resolveSigningProperty(name: String): String? {
    val envName = "VYLLO_${name.uppercase()}"
    return System.getenv(envName)
        ?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() && !it.startsWith("CHANGE_ME") }
}

fun resolveSecretProperty(name: String, envName: String): String? {
    return System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() && !it.startsWith("CHANGE_ME") }
}

// Public SPKI pins for api.github.com (leaf + Sectigo intermediate + root).
// Pins are public key hashes, not secrets â€” safe to commit. They cover the
// in-app update metadata endpoint only; asset hosts stay unpinned on purpose.
// Rotate by setting VYLLO_CERT_PINS (or certPins in keystore.local.properties).
val defaultCertPins = listOf(
    "api.github.com=sha256/S2LUIbq4yUg5w+MYbj5LZOWAZAzaeNGJ9rTTc4GjvBQ=",
    "api.github.com=sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=",
    "api.github.com=sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE="
).joinToString(",")

android {
    namespace = "com.vyllo.music"
    // API 36 (Android 16): required by Google Play for all app updates as of
    // 2026-08-31. Platform 36 is installed locally; AGP 8.6.1 compiles it fine.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vyllo.music"
        minSdk = 24
        targetSdk = 36
        versionCode = 14
        versionName = "v2.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Sensitive values come from env vars or local (gitignored) properties â€” never from source.
        // See keystore.local.properties.example for the full list of supported keys.
        buildConfigField("String", "LYRICS_API_BASE", "\"https://lrclib.net/api\"")
        buildConfigField("String", "NETEASE_SEARCH_API", "\"https://music.163.com/api/search/get\"")
        buildConfigField("String", "NETEASE_LYRIC_API", "\"https://music.163.com/api/song/lyric\"")
        buildConfigField("String", "DNS_OVER_HTTPS_URL", "\"https://dns.google/dns-query\"")
        buildConfigField(
            "String",
            "GOOGLE_API_KEY",
            "\"${resolveSecretProperty("googleApiKey", "VYLLO_GOOGLE_API_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "CERT_PINS",
            "\"${resolveSecretProperty("certPins", "VYLLO_CERT_PINS") ?: defaultCertPins}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // This version is strictly tied to Kotlin 1.9.24
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/ASL2.0")
    }
    
    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore
        }
        create("release") {
            val storeFilePath = resolveSigningProperty("storeFile")
            storeFile = storeFilePath?.let { path ->
                val candidate = File(path)
                if (candidate.isAbsolute) candidate else file("../$path")
            }
            storePassword = resolveSigningProperty("storePassword")
            keyAlias = resolveSigningProperty("keyAlias")
            keyPassword = resolveSigningProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    
    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Real devices are arm64-v8a/armeabi-v7a â€” x86/x86_64 .so files only
            // serve emulators. Dropping them from release cuts download/install
            // size and install-time dexopt work. Debug keeps all ABIs so
            // emulator testing is unaffected.
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }

            val storeFilePath = resolveSigningProperty("storeFile")
            signingConfig = if (storeFilePath != null) {
                signingConfigs.getByName("release")
            } else {
                // Never throw at configuration time: that breaks `test`, `lint`
                // and every CI job on clean checkouts. Fail only when the
                // release APK is actually assembled (task graph below).
                signingConfigs.getByName("debug")
            }
        }

        // Macrobenchmark / baseline-profile target.
        //
        // Release is minified and signed with the production keystore, which makes it
        // unusable as a benchmark target (no readable symbols in the trace, and the
        // keystore may be absent on CI). This build type behaves exactly like release
        // for the user-visible work we are measuring (same R8 output via the fallback)
        // but signs with the debug key so `adb install` always works.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }

    // Gate real release packaging on credentials without poisoning configuration.
    // Pass -PallowDebugRelease to explicitly permit a debug-signed release build.
    val requireReleaseSigning = tasks.register("requireReleaseSigning") {
        doFirst {
            if (project.hasProperty("allowDebugRelease")) {
                logger.warn("WARNING: building release with DEBUG signing (-PallowDebugRelease). NOT distributable.")
                return@doFirst
            }
            val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .filter { resolveSigningProperty(it) == null }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "RELEASE SIGNING CONFIG IS MISSING ($missing).\n" +
                    "Create 'keystore.local.properties' in the project root with:\n" +
                    "  storeFile=relative/path/to/keystore.jks\n" +
                    "  storePassword=...\n" +
                    "  keyAlias=...\n" +
                    "  keyPassword=...\n" +
                    "  googleApiKey=...  # optional, YouTube BotGuard\n\n" +
                    "Or set VYLLO_STOREFILE / VYLLO_STOREPASSWORD / VYLLO_KEYALIAS / " +
                    "VYLLO_KEYPASSWORD / VYLLO_GOOGLE_API_KEY environment variables.\n" +
                    "See keystore.local.properties.example."
                )
            }
        }
    }
    tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
        dependsOn(requireReleaseSigning)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    // Room's MigrationTestHelper reads exported schema JSONs from androidTest assets
    // (ksp room.schemaLocation alone does not wire them into the test APK).
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    lint {
        // Fail the build on real errors (catches NewApi / missing permissions / etc.)
        checkReleaseBuilds = true
        abortOnError = true
        // NOTE: MissingTranslation is deliberately NOT disabled — values-es must
        // stay complete. Add a locale, add its translations, in the same change.
        // Media3 intentionally exposes many APIs as @UnstableApi; we pin versions
        // and verify at runtime, so project-wide opt-in is safe here.
        disable += "UnsafeOptInUsageError"
        // Release deliberately ships arm-only (see ndk.abiFilters above) so the
        // universal APK stays small; Play filters ABI per device anyway. ChromeOS
        // / x86_64 users are excluded on purpose rather than by accident.
        disable += "ChromeOsAbiSupport"
        // "A newer version is available" advisories, not defects. Several are
        // hard pins: kotlinx-serialization 1.6.x is the last line that compiles
        // on Kotlin 1.9.24, and Room / Hilt / Compose-BOM / Media3 upgrades all
        // need a Kotlin 2.x + AGP toolchain bump first (attempted: Datastore
        // 1.2.1 alone drags serialization 1.7.3 in, which refuses to compile).
        // Freshness is tracked by .github/dependabot.yml instead of lint.
        disable += "GradleDependency"
    }
}

// Enable Compose Strong Skipping Mode for better scroll performance
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:strongSkipping=true"
        )

        // Opt-in Compose compiler instrumentation, used to find which composables are
        // recomposing unskippable during a scroll. Off by default so normal builds pay
        // nothing for it.
        //
        //   ./gradlew :app:assembleBenchmark -PcomposeMetrics
        //
        // Output: app/build/composeMetrics/metrics/  (per-file recomposition counts)
        //         app/build/composeMetrics/reports/  (stability + skippability reports)
        if (project.hasProperty("composeMetrics")) {
            val out = layout.buildDirectory.dir("composeMetrics").get().asFile
                .absolutePath.replace('\\', '/')
            freeCompilerArgs.addAll(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$out/metrics",
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$out/reports"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Release-only: drop x86/x86_64 native libs (emulator-only ABIs). Real devices
// are arm64-v8a/armeabi-v7a; the bundled .so files are androidx helpers.
// NOTE: applied via variant-aware packaging below in validateRelease block.

// Post-build gate: verify the release APK signature + alignment so a
// wrong-signer or misaligned artifact can never be distributed by accident.
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.exists()) throw GradleException("Release APK not found: ${apk.absolutePath}")
        val sdkDir = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("ANDROID_HOME/ANDROID_SDK_ROOT not set; cannot run apksigner")
        val buildTools = File(sdkDir, "build-tools").listFiles()
            ?.maxByOrNull { it.name }?.absolutePath
            ?: throw GradleException("No build-tools found under $sdkDir")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val apksigner = "$buildTools/apksigner${if (isWindows) ".bat" else ""}"
        val zipalign = "$buildTools/zipalign${if (isWindows) ".exe" else ""}"
        val verifyResult = providers.exec {
            commandLine(apksigner, "verify", "--print-certs", "--verbose", apk.absolutePath)
        }
        val out = verifyResult.standardOutput.asText.get()
        if (!out.contains("Verified using v2 scheme (APK Signature Scheme v2): true")) {
            throw GradleException("Release APK is NOT v2-signed:\n$out")
        }
        providers.exec {
            commandLine(zipalign, "-c", "-p", "4", apk.absolutePath)
        }.result.get().assertNormalExitValue()
        val expectedSha = "94ea48d066fe5c1c1862ff03d9e5777ca2b912cd0b140c5d817c1c732e587f8f"
        if (!out.lowercase().contains(expectedSha) && !project.hasProperty("allowDebugRelease")) {
            throw GradleException("Release APK signer does not match the expected Vyllo release certificate ($expectedSha). Refusing to accept an incompatible update signer.\n$out")
        }
        if (project.hasProperty("allowDebugRelease")) {
            logger.warn(
                "WARNING: skipped production-certificate check for explicitly requested debug release. " +
                "This APK is not distributable."
            )
        }
        logger.lifecycle("validateReleaseSigning: v2-signed, zipalign OK: ${apk.name}")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(validateReleaseSigning)
}

dependencies {
    // Baseline profile: merges :baseline-profile output into the APK/AAB.
    // (Also pulls in androidx.profileinstaller so the profile is usable on all API levels.)
    baselineProfile(project(":baseline-profile"))

    // NewPipe Extractor
    implementation("com.github.teamnewpipe:newpipeextractor:v0.26.3")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Security - Encrypted Storage
    implementation("androidx.security:security-crypto-ktx:1.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-session:1.3.0")
    implementation("androidx.media3:media3-common:1.3.0")
    implementation("androidx.media3:media3-database:1.3.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")

    // UI (Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Extended Material Icons (version managed by the Compose BOM)
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle (collectAsStateWithLifecycle - avoids bg recomposition)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // JSON parsing (JVM-only: Android ships org.json in the platform)
    testImplementation("org.json:json:20210307")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    
    // Lifecycle Service
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    
    // Coroutines Guava (for ListenableFuture)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    
    // Room Database (for download metadata)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // WorkManager (for reliable background downloads)
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ConstraintLayout for alarm activity
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Mockito for unit tests
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Leak detection â€” debug only. Releases are unaffected (LeakCanary's own
    // artifact is already a no-op outside debug, but we keep it off the release
    // classpath entirely so R8 never sees it).
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Serialization (1.6.x is the last line compatible with Kotlin 1.9.x;
    // 1.7.x requires Kotlin 2.0+ at compile time)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
