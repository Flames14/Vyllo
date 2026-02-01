# Keep NewPipe Extractor classes
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Keep Coil image loading
-keep class coil.** { *; }

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Compose classes  
-keep class androidx.compose.** { *; }

# Keep ExoPlayer/Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep reflection for serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Missing classes from NewPipeExtractor
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# General Android rules
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
