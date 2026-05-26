# =====================================================================
# Helix Browser - ProGuard / R8 rules
# =====================================================================
# Goal: keep R8 full-mode + resource shrinking enabled while preserving
# anything reflected on (WebView JS bridges, Room, Glide, Billing, Parcelize).
# =====================================================================

# ----- Kotlin metadata & coroutines -----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ----- Parcelize / Parcelable -----
-keep class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ----- AndroidX core -----
-keep class androidx.lifecycle.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.preference.** { *; }

# ----- WebView JS interfaces & callbacks -----
# Anything annotated with @JavascriptInterface MUST be preserved or JS bridges
# silently break when R8 obfuscates the method name.
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.helix.browser.engine.** { *; }

# WebViewClient / WebChromeClient overrides are called by the framework.
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }
-keep class * extends android.webkit.WebView { *; }

# ----- Room (KSP generates _Impl classes referenced by name) -----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class **_Impl { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# ----- Glide -----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
-keep class com.bumptech.glide.** { *; }

# ----- Google Play Billing -----
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ----- App data classes referenced from JSON / Room / Glide / IPC -----
-keep class com.helix.browser.data.** { *; }
-keep class com.helix.browser.tabs.** { *; }
-keep class com.helix.browser.billing.** { *; }

# ----- Crash readability: preserve line numbers but obfuscate names -----
-keepattributes LineNumberTable,SourceFile

# ----- Strip noisy logs in release -----
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# ----- Suppress legitimate warnings from optional deps -----
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
