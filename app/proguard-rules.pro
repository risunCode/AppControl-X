# AppControlX ProGuard Rules

# Keep attributes for debugging
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep Shizuku (critical for release builds)
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class * implements android.os.IInterface {
    public *;
}

# Keep libsu
-keep class com.topjohnwu.superuser.** { *; }

# Keep AIDL interfaces
-keep class com.appcontrolx.IShellService { *; }
-keep class com.appcontrolx.IShellService$* { *; }
-keep class * extends android.os.Binder { *; }

# Keep Shizuku UserService
-keep class com.appcontrolx.executor.ShellService { *; }
-keep class com.appcontrolx.executor.ShizukuExecutor { *; }
-keep class com.appcontrolx.executor.ShizukuExecutor$* { *; }

# Keep all executors
-keep class com.appcontrolx.executor.** { *; }

# Keep MainActivity methods (What's New dialog)
-keepclassmembers class com.appcontrolx.ui.MainActivity {
    private void showWhatsNewIfNeeded();
    private void showWhatsNewDialog();
}

# Keep Models
-keep class com.appcontrolx.model.** { *; }
-keep class com.appcontrolx.data.local.entity.** { *; }

# Keep Rollback data classes (for Gson serialization)
-keep class com.appcontrolx.rollback.** { *; }

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Hilt
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
