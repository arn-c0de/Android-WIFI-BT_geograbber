# WiFi GeoGrabber ProGuard Configuration
# This file contains ProGuard/R8 rules for code optimization and security

# ============================================================================
# SECURITY: Remove all Log statements from production builds
# ============================================================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# ============================================================================
# SQLCipher - Keep necessary classes for encrypted database
# ============================================================================
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

# SQLCipher database classes
-keep class net.sqlcipher.database.** { *; }
-keep class net.sqlcipher.database.SQLiteDatabase { *; }
-keep class net.sqlcipher.database.SQLiteOpenHelper { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# AndroidX Biometric - Keep necessary classes for fingerprint/face auth
# ============================================================================
-keep class androidx.biometric.** { *; }
-keep interface androidx.biometric.** { *; }

# Keep BiometricPrompt and related classes
-keep class androidx.biometric.BiometricPrompt { *; }
-keep class androidx.biometric.BiometricPrompt$** { *; }
-keep class androidx.biometric.BiometricManager { *; }

# ============================================================================
# Android Keystore - Keep crypto classes
# ============================================================================
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# ============================================================================
# Keep application classes with important functionality
# ============================================================================
-keep public class com.example.wifi_geograbber.EncryptionManager { *; }
-keep public class com.example.wifi_geograbber.DatabaseEncryptionHelper { *; }
-keep public class com.example.wifi_geograbber.BiometricAuthManager { *; }
-keep public class com.example.wifi_geograbber.SecureFileDelete { *; }

# Keep MainActivity and Activities
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# ============================================================================
# WebView JavaScript Interface (for MapActivity)
# ============================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface
-keepattributes *Annotation*

# Keep JavaScript interface classes
-keep class com.example.wifi_geograbber.MapActivity$** { *; }

# ============================================================================
# Serialization / Parceling
# ============================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# Google Play Services
# ============================================================================
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ============================================================================
# Optimization settings
# ============================================================================
# Enable aggressive optimizations
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization methods (safe options)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ============================================================================
# Debugging attributes (comment out for production)
# ============================================================================
# Keep source file and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep exception information
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod

# ============================================================================
# General Android rules
# ============================================================================
-keep class * extends androidx.fragment.app.Fragment {}
-keep class * extends androidx.appcompat.app.AppCompatActivity {}

# Keep View constructors (needed for XML inflation)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep setters and getters (might be used via reflection)
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

# ============================================================================
# Enum optimization
# ============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# R8 Full Mode (if using R8 instead of ProGuard)
# ============================================================================
# R8 is enabled by default in Android Gradle Plugin 3.4.0+
# These rules ensure compatibility with R8's full mode

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
