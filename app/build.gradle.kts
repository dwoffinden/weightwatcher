plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.daw.weightwatcher"
    compileSdk = 37
    // compileSdkExtension = 22
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.daw.weightwatcher"
        minSdk = 34
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    kotlin {
        jvmToolchain(25)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.2.0-alpha03")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Jetpack Glance
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha03")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.google.truth:truth:1.4.2")
}
