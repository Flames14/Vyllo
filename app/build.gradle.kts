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

android {
    namespace = "com.vyllo.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vyllo.music"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Security: BuildConfig fields for sensitive URLs (not hardcoded in source)
        buildConfigField("String", "LYRICS_API_BASE", "\"https://lrclib.net/api\"")
        buildConfigField("String", "NETEASE_SEARCH_API", "\"https://music.163.com/api/search/get\"")
        buildConfigField("String", "NETEASE_LYRIC_API", "\"https://music.163.com/api/song/lyric\"")
        buildConfigField("String", "DNS_OVER_HTTPS_URL", "\"https://dns.google/dns-query\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // This version is strictly tied to Kotlin 1.9.22
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
    
    // Use debug signing for release builds (for testing only)
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
            // Enable some optimizations for smoother 120Hz testing
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val hasReleaseSigning = resolveSigningProperty("storeFile") != null &&
                resolveSigningProperty("storePassword") != null &&
                resolveSigningProperty("keyAlias") != null &&
                resolveSigningProperty("keyPassword") != null

            if (!hasReleaseSigning) {
                throw GradleException(
                    "RELEASE SIGNING CONFIG IS MISSING.\n" +
                    "To fix this, create a file named 'keystore.local.properties' in the project root with:\n" +
                    "  storeFile=relative/path/to/keystore.jks\n" +
                    "  storePassword=your_store_password\n" +
                    "  keyAlias=your_key_alias\n" +
                    "  keyPassword=your_key_password\n\n" +
                    "Or set VYLLO_STOREFILE, VYLLO_STOREPASSWORD, VYLLO_KEYALIAS, VYLLO_KEYPASSWORD environment variables.\n" +
                    "See keystore.local.properties.example for reference."
                )
            }

            signingConfig = signingConfigs.getByName("release")
        }
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

dependencies {
    // NewPipe Extractor
    implementation("com.github.teamnewpipe:newpipeextractor:v0.26.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Security - Encrypted Storage
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-session:1.3.0")
    implementation("androidx.media3:media3-common:1.3.0")
    implementation("androidx.media3:media3-database:1.3.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")

    // UI (Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Palette API (for extracting colors from album art)
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Extended Material Icons (for Play/Pause/Skip icons)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // JSON parsing
    implementation("org.json:json:20210307")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Lifecycle Service
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    
    // Coroutines Guava (for ListenableFuture)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    
    // Room Database (for download metadata)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // WorkManager (for reliable background downloads)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // ConstraintLayout for alarm activity
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Mockito for unit tests
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
