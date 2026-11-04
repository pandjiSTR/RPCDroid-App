plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.rpcdroid.nr"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.rpcdroid.nr"
        minSdk = 29            // sama dengan RPCDroid asli (Android 10+)
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Pakai debug keystore agar APK release langsung bisa diinstal (sideload, bukan Play Store).
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; aidl = true }
}

dependencies {
    // Satu-satunya dependency: WebSocket + TLS + HTTP. Menggantikan mbedTLS + websocket.c (743 baris) di daemon asli.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Shizuku: akses shell tanpa root (UserService)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // UI: Jetpack Compose + Material 3 (hanya icons-core; extended terlalu besar)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
