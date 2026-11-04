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

val gsFile = file("google-services.json")
val hasWebClientInGoogleServices = gsFile.exists() &&
    Regex(""""client_type"\s*:\s*3""").containsMatchIn(gsFile.readText())

val bundledJksFile = file("ontik-release-key.jks")

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

        // Real AdMob App ID & Real Banner Ad Unit ID across all builds
        manifestPlaceholders["admobAppId"] = "ca-app-pub-9544511312663053~8782253318"
        buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-9544511312663053~8782253318\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-9544511312663053/2332395038\"")

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
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "ontik-release-key.jks")
                storePassword = keystoreProperties.getProperty("storePassword")
                enableV1Signing = true
                enableV2Signing = true
            } else if (bundledJksFile.exists()) {
                storeFile = bundledJksFile
                storePassword = "OntikCountAi2026"
                keyAlias = "ontik_key"
                keyPassword = "OntikCountAi2026"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (bundledJksFile.exists() || keystorePropertiesFile.exists()) {
                signingConfigs.getByName("ontikPermanent")
            } else {
                signingConfigs.getByName("debug")
            }

            manifestPlaceholders["admobAppId"] = "ca-app-pub-9544511312663053~8782253318"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-9544511312663053~8782253318\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-9544511312663053/2332395038\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = if (bundledJksFile.exists() || keystorePropertiesFile.exists()) {
                signingConfigs.getByName("ontikPermanent")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = true

            // Real AdMob App ID & Real Banner Ad Unit ID in debug builds too as requested by user
            manifestPlaceholders["admobAppId"] = "ca-app-pub-9544511312663053~8782253318"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-9544511312663053~8782253318\"")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-9544511312663053/2332395038\"")
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.opencv)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics.ktx)

    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.revenuecat.purchases)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.services.ads)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
}
