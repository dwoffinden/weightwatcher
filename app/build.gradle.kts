plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.daw.weightwatcher"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.daw.weightwatcher"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha03")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.google.truth:truth:1.4.2")
}
