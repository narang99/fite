plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.fiteduck.phoneagent"
    // Compose 1.13 needs platform 37.1 or newer to compile. The phone itself runs 36.
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "dev.fiteduck.phoneagent"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "HOST_URL", "\"ws://127.0.0.1:8765\"")
        buildConfigField("String", "HOST_TOKEN", "\"dev-token\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
}
