# Keep App Models unconditionally for JSON Serialization
-keep class com.example.musicpiped.** { *; }

# Keep NewPipe Extractor classes
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**

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

# Keep missing packages used by NewPipeExtractor
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Suppress warnings for javax.script which Android doesn't have but Rhino references
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# Keep serializable 
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# General Android rules
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
