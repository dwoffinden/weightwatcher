plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.protobuf")
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

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  buildFeatures {
    compose = true
    viewBinding = true
  }

  kotlin { jvmToolchain(25) }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.13.0")

  // Compose
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation(platform("androidx.compose:compose-bom:2026.04.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")

  // Health Connect
  implementation("androidx.health.connect:connect-client:1.2.0-alpha04")

  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.11.2")

  // Jetpack Glance
  implementation("androidx.glance:glance-appwidget:1.1.1")
  implementation("androidx.glance:glance-material3:1.1.1")

  // DataStore
  implementation("androidx.datastore:datastore:1.2.1")
  implementation("com.google.protobuf:protobuf-kotlin-lite:4.34.1")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  // Testing
  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha03")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("com.google.truth:truth:1.4.5")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.34.1" }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        register("java") { option("lite") }
        register("kotlin") { option("lite") }
      }
    }
  }
}
