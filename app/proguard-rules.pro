# Add project specific ProGuard rules here.
-keep class org.tensorflow.lite.** { *; }
-keep class com.ontik.countai.ai.** { *; }
-keep class com.ontik.countai.data.local.** { *; }
-dontwarn org.tensorflow.lite.**
