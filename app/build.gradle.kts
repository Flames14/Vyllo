import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
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
// Pins are public key hashes, not secrets — safe to commit. They cover the
// in-app update metadata endpoint only; asset hosts stay unpinned on purpose.
// Rotate by setting VYLLO_CERT_PINS (or certPins in keystore.local.properties).
val defaultCertPins = listOf(
    "api.github.com=sha256/S2LUIbq4yUg5w+MYbj5LZOWAZAzaeNGJ9rTTc4GjvBQ=",
    "api.github.com=sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=",
    "api.github.com=sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE="
).joinToString(",")

android {
    namespace = "com.vyllo.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vyllo.music"
        minSdk = 24
        targetSdk = 35
        versionCode = 13
        versionName = "v2.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Sensitive values come from env vars or local (gitignored) properties — never from source.
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
            storeFile = storeFilePath?.let { file("../$it") }
            storePassword = resolveSigningProperty("storePassword")
            keyAlias = resolveSigningProperty("keyAlias")
            keyPassword = resolveSigningProperty("keyPassword")
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
    }

    // Gate real release packaging on credentials without poisoning configuration.
    val requireReleaseSigning = tasks.register("requireReleaseSigning") {
        doFirst {
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

    lint {
        // Fail the build on real errors (catches NewApi / missing permissions / etc.)
        checkReleaseBuilds = true
        abortOnError = true
        // Obsolete/missing-resource noise should not block CI while we clean up strings.
        disable += "MissingTranslation"
        // Media3 intentionally exposes many APIs as @UnstableApi; we pin versions
        // and verify at runtime, so project-wide opt-in is safe here.
        disable += "UnsafeOptInUsageError"
    }
}

// Enable Compose Strong Skipping Mode for better scroll performance
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // NewPipe Extractor
    implementation("com.github.teamnewpipe:newpipeextractor:v0.26.3")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Security - Encrypted Storage
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
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
    implementation("androidx.activity:activity-compose:1.9.2")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Palette API (for extracting colors from album art)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Extended Material Icons (version managed by the Compose BOM)
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // JSON parsing (JVM-only: Android ships org.json in the platform)
    testImplementation("org.json:json:20210307")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Lifecycle Service
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    
    // Coroutines Guava (for ListenableFuture)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    
    // Room Database (for download metadata)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // WorkManager (for reliable background downloads)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ConstraintLayout for alarm activity
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Mockito for unit tests
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Serialization (1.6.x is the last line compatible with Kotlin 1.9.x;
    // 1.7.x requires Kotlin 2.0+ at compile time)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
