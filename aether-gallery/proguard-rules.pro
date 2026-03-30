# AetherSuite ProGuard — aether-gallery
-keep class com.aether.core.** { *; }
-dontwarn coil.**
-keep class androidx.biometric.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.media.** { *; }
-assumenosideeffects class android.util.Log {
    public static *** d(...); public static *** v(...); public static *** i(...);
}
