# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===================================================================
# KTOR & HTTP CLIENT PROGUARD / R8 RULES
# Prevents R8 from stripping io.ktor classes, interfaces, and plugins
# (such as io.ktor.client.plugins.HttpTimeout) used by Google AI SDK
# ===================================================================
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-dontwarn io.ktor.**

# Preserve Ktor client plugins specifically
-keep class io.ktor.client.plugins.** { *; }

# Google Generative AI SDK reflection & serialization rules
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

