import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.example.musicpiped"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.musicpiped"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
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
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            storeFile = storeFilePath?.let { file("../$it") }
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
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
            // Sign with actual release key
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
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.25.2")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-session:1.3.0")
    implementation("androidx.media3:media3-common:1.3.0")
    implementation("androidx.media3:media3-database:1.3.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.0")

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

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}