-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn android.content.res.**
-dontwarn org.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn java.lang.reflect.AnnotatedType
-keepnames class * { *; }
-keepnames interface * { *; }
-keepnames enum * { *; }
-keep class android.content.** { *; }
-keep class **.model.** { *; }
-keep class **.models.** { *; }
# === Fix D8/R8 crash: skip Sora Kotlin metadata ===
-dontwarn io.github.rosemoe.sora.**
-dontwarn io.github.Rosemoe.**
-keep class io.github.rosemoe.sora.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-dontwarn kotlin.**
