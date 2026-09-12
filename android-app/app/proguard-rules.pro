# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# JNI & Native Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.qazar.pdfviewer.bridge.** { *; }
-keep class com.qazar.pdfviewer.data.** { *; }
-keep class com.qazar.pdfviewer.ui.viewer.ArchitectureTelemetryState { *; }

# Aggressive Obfuscation for Maximum Security
-repackageclasses ''
-flattenpackagehierarchy ''
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature
