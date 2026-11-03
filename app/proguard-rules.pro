# ProGuard / R8 Keep Rules for OpenCV, Firebase, RevenueCat, Room, and Hilt
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Room & Firestore Models
-keep class com.ontik.countai.data.local.** { *; }
-keep class com.ontik.countai.data.remote.** { *; }
-keep class com.ontik.countai.domain.model.** { *; }

# Firebase & Google Play Services
-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# RevenueCat Purchases SDK
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**
