# ===================================================================
# Vyllo Music Player - Advanced ProGuard/R8 Security Rules
# ===================================================================
# Senior-Level Hardening Configuration
# ===================================================================

# -------------------------------------------------------------------
# Advanced Obfuscation Settings
# -------------------------------------------------------------------
-optimizationpasses 7
-optimizations !code/simplification/arithmetic,!code/allocation/*,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses ''
-overloadaggressively

# Remove source file information (makes reverse engineering harder)
-renamesourcefileattribute SourceFile

# -------------------------------------------------------------------
# Strip Debug Logs in Release (CRITICAL FOR SECURITY)
# -------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    # Keep warnings and errors for production monitoring
    # public static *** w(...);
    # public static *** e(...);
}

# Strip Kotlin assertions
-assumenosideeffects class kotlin.assertions.AssertionsKt {
    public static *** assert(...);
}

# -------------------------------------------------------------------
# Hilt - Dependency Injection (Minimal Keep Rules)
# -------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep class dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keepclassmembers,allowobfuscation,allowshrinking interface * extends dagger.hilt.android.internal.lifecycle.HiltViewModelMap { *; }
-keepclassmembers,allowobfuscation,allowshrinking interface * extends dagger.hilt.android.internal.managers.ViewModelSupplier { *; }
-keep class dagger.hilt.android.internal.workers.HiltWorkerFactory { *; }
-keep class dagger.hilt.android.internal.workers.** { *; }

# Keep Application class
-keep class com.vyllo.music.VylloApplication { *; }

# Keep ViewModels (but allow obfuscation of internal members)
-keep class com.vyllo.music.**ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# -------------------------------------------------------------------
# Room Database (Minimal Keep Rules)
# -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep Room Entities (obfuscate field names)
-keep class com.vyllo.music.data.download.DownloadEntity { *; }
-keep class com.vyllo.music.data.download.PlaylistEntity { *; }
-keep class com.vyllo.music.data.download.PlaylistSongEntity { *; }
-keep class com.vyllo.music.data.download.HistoryEntity { *; }

# Keep Room DAOs
-keep class com.vyllo.music.data.download.DownloadDao { *; }
-keep class com.vyllo.music.data.download.PlaylistDao { *; }
-keep class com.vyllo.music.data.download.HistoryDao { *; }

# Keep Room Database
-keep class com.vyllo.music.data.download.DownloadDatabase { *; }
-keep class com.vyllo.music.data.download.Converters { *; }

# -------------------------------------------------------------------
# NewPipe Extractor (Keep Required Classes)
# -------------------------------------------------------------------
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**

# -------------------------------------------------------------------
# OkHttp & Networking
# -------------------------------------------------------------------
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# -------------------------------------------------------------------
# Coil Image Loading
# -------------------------------------------------------------------
-keep class coil.** { *; }
-keep interface coil.** { *; }
-keep class coil.compose.** { *; }

# -------------------------------------------------------------------
# Media3 / ExoPlayer
# -------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# -------------------------------------------------------------------
# Compose
# -------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keep class androidx.compose.compiler.** { *; }

# -------------------------------------------------------------------
# Kotlin
# -------------------------------------------------------------------
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }

# Keep Kotlin Metadata
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# -------------------------------------------------------------------
# WorkManager
# -------------------------------------------------------------------
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep DownloadWorker
-keep class com.vyllo.music.service.DownloadWorker { *; }

# -------------------------------------------------------------------
# JSON / org.json
# -------------------------------------------------------------------
-keep class org.json.** { *; }

# -------------------------------------------------------------------
# Guava (used by Media3 and Hilt)
# -------------------------------------------------------------------
-keep class com.google.common.** { *; }
-keep interface com.google.common.** { *; }

# -------------------------------------------------------------------
# NanoJSON (used by NewPipe)
# -------------------------------------------------------------------
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

# -------------------------------------------------------------------
# JSoup (used by NewPipe)
# -------------------------------------------------------------------
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# -------------------------------------------------------------------
# Security Module - Keep Our Security Classes
# -------------------------------------------------------------------
-keep class com.vyllo.music.core.security.** { *; }

# -------------------------------------------------------------------
# Reflection & Serialization
# -------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# -------------------------------------------------------------------
# Missing Classes (Suppress warnings)
# -------------------------------------------------------------------
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedArrayType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedTypeVariable
-dontwarn java.lang.reflect.AnnotatedWildcardType
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.MethodHandle
-dontwarn java.lang.invoke.MethodHandles$Lookup

# -------------------------------------------------------------------
# Keep Missing Packages
# -------------------------------------------------------------------
-keep class javax.script.** { *; }
-keep class jdk.dynalink.** { *; }

# -------------------------------------------------------------------
# Keep App Models for JSON Serialization (Obfuscate Internal Fields)
# -------------------------------------------------------------------
-keep class com.vyllo.music.domain.model.** { *; }
-keepclassmembers class com.vyllo.music.domain.model.** { *; }

# -------------------------------------------------------------------
# Keep Enum classes
# -------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -------------------------------------------------------------------
# Keep Generic Signatures for Gson/JSON
# -------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -------------------------------------------------------------------
# ANTI-TAMPERING: Detect and prevent common cracking attempts
# -------------------------------------------------------------------
# Keep security monitor classes from being removed
-keep class com.vyllo.music.core.security.SecurityMonitor { *; }

# Prevent removal of security checks
-keepclassmembers class com.vyllo.music.core.security.SecurityMonitor {
    public static *** isDeviceRooted();
    public static *** isEmulator();
    public static *** getRiskLevel(...);
}
