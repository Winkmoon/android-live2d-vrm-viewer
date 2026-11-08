# Keep JNI entry points and the app's bridge classes.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.winkmoon.xinran.Live2DNative { *; }
-keep class com.winkmoon.xinran.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
