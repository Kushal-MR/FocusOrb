plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kushal.focusorb"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.kushal.focusorb"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

    }

    buildTypes {
        release {
            // Optimised and non-debuggable. A debuggable Compose build runs
            // markedly slower — on a 120 Hz phone the app picker missed its
            // 8.3 ms frame budget on ~10% of frames, which reads as 60 fps.
            isMinifyEnabled = true
            isShrinkResources = true
            // Signed with the local debug key so releases install over earlier
            // builds without a separate keystore. Fine for sideloaded APKs;
            // a Play Store build would need a real upload key.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.material.icons.core)
    implementation(libs.wear.ongoing)
    implementation(libs.wear)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.viewmodel.compose)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}