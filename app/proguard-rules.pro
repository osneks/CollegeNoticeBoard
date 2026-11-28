# ProGuard / R8 rules for College Notice Board

# Keep OkHttp & SSE
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep Jsoup HTML parser
-dontwarn org.jsoup.**
-keep class org.jsoup.nodes.** { *; }

# Keep Kotlinx Serialization models & Navigation3 keys
-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.collegenoticeboard.**$$serializer { *; }
-keep class com.example.collegenoticeboard.data.** { *; }
-keep class com.example.collegenoticeboard.Main { *; }

# Keep ViewModels instantiated via reflection by ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# Keep WorkManager, Room (WorkDatabase_Impl), and App Startup initializers under R8 full mode
-keep class * extends androidx.room.RoomDatabase { *; }
-keepnames class * extends androidx.room.RoomDatabase
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.startup.Initializer { <init>(...); }

# Keep app entry points & receivers
-keep class com.example.collegenoticeboard.CollegeNoticeBoardApp { <init>(); }
-keep class com.example.collegenoticeboard.MainActivity { *; }
-keep class com.example.collegenoticeboard.data.NoticeCheckWorker { *; }
-keep class com.example.collegenoticeboard.data.BootReceiver { *; }

# Strip debug logs in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
