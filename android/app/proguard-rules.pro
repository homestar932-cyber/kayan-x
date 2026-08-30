# Kayan X ProGuard rules
-keep class com.kayanx.android.native.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Keep agent models for serialization
-keep class com.kayanx.android.agent.** { *; }
-keep class com.kayanx.android.fs.model.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
