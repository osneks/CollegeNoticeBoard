# Proguard / R8 rules for College Notice Board

# Keep OkHttp & SSE
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Jsoup
-keep class org.jsoup.** { *; }

# Keep Data Models
-keep class com.example.collegenoticeboard.data.** { *; }
