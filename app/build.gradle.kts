import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Check if app/google-services.json already defines an OAuth web client (client_type: 3).
// If so, the google-services plugin generates R.string.default_web_client_id automatically.
// If not, provide an empty default_web_client_id string resource so R.string.default_web_client_id always compiles.
val gsFile = file("google-services.json")
val hasWebClientInGoogleServices = gsFile.exists() &&
    Regex(""""client_type"\s*:\s*3""").containsMatchIn(gsFile.readText())

android {
    namespace = "com.ontik.countai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ontik.countai"
        minSdk = 26
        targetSdk = 34
        versionCode = 100 + ciRunNumber
        versionName = "3.0.$ciRunNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 64-bit & ARM optimization for Play Store .aab and fast .apk downloads (~30MB)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        val rcKey = (project.findProperty("REVENUECAT_ANDROID_API_KEY") as? String)
            ?: System.getenv("REVENUECAT_ANDROID_API_KEY")
            ?: ""
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$rcKey\"")

        if (!hasWebClientInGoogleServices) {
            resValue("string", "default_web_client_id", "\"\"")
        }
    }

    signingConfigs {
        create("ontikPermanent") {
            val bundledJks = file("ontik-release-key.jks")
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "ontik-release-key.jks")
                storePassword = keystoreProperties.getProperty("storePassword")
            } else if (bundledJks.exists()) {
                storeFile = bundledJks
                storePassword = "OntikCountAi2026"
                keyAlias = "ontik_key"
                keyPassword = "OntikCountAi2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("ontikPermanent")

            // Real AdMob App ID & Real Banner Ad Unit ID ONLY in release builds
            manifestPlaceholders["admobAppId"] = "ca-app-pub-9544511312663053~8782253318"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-9544511312663053~8782253318\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-9544511312663053/2332395038\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Same applicationId ("com.ontik.countai") and permanent keystore for seamless in-place updates
            signingConfig = signingConfigs.getByName("ontikPermanent")
            isDebuggable = true

            // Official Google Test App ID & Test Adaptive/Standard Banner Ad Unit ID in debug builds
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so", "**/libopencv_java4.so")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core & Splash Screen
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose UI & Material 3
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Hilt Dependency Injection + WorkManager
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // CameraX & OpenCV 4.9.0
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.opencv)

    // Room Database & Encrypted SharedPreferences / DataStore
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Firebase BoM, Auth, Firestore, Remote Config, Analytics, Crashlytics, App Check
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.appcheck.playintegrity)

    // Google Sign-In: Play Services Auth + Credential Manager API + Google ID
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // RevenueCat Play Billing, In-App Review, In-App Updates, Google Mobile Ads (AdMob)
    implementation(libs.revenuecat.purchases)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.services.ads)

    // Coroutines & Utilities
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.exifinterface)

    // Unit Testing
    testImplementation(libs.junit)
}
