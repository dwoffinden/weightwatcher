plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  id("com.google.protobuf")
}

android {
  namespace = "dev.daw.weightwatcher"
  compileSdk = 37
  buildToolsVersion = "37.0.0"

  defaultConfig {
    applicationId = "dev.daw.weightwatcher"
    minSdk = 34
    targetSdk = 37
    versionCode = 7
    versionName = "7"
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  buildFeatures { compose = true }

  kotlin { jvmToolchain(25) }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation("androidx.core:core-ktx:1.19.1")

  // Compose
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")

  // Health Connect
  implementation("androidx.health.connect:connect-client:1.2.0-alpha06")

  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.12.0")

  // Jetpack Glance
  implementation("androidx.glance:glance-appwidget:1.1.1")
  implementation("androidx.glance:glance-material3:1.1.1")

  // DataStore
  implementation("androidx.datastore:datastore:1.3.0")
  implementation("com.google.protobuf:protobuf-kotlin-lite:4.36.2")

  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  // Hilt
  implementation("com.google.dagger:hilt-android:2.60.1")
  ksp("com.google.dagger:hilt-android-compiler:2.60.1")
  implementation("androidx.hilt:hilt-work:1.4.0")
  ksp("androidx.hilt:hilt-compiler:1.4.0")

  // Testing
  testImplementation("junit:junit:4.13.2")
  testImplementation(kotlin("test"))
  testImplementation("com.google.dagger:hilt-android-testing:2.60.1")
  kspTest("com.google.dagger:hilt-android-compiler:2.60.1")
  testImplementation("com.google.testparameterinjector:test-parameter-injector:1.22")
  testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha04")
  testImplementation("com.google.truth:truth:1.4.5")
  testImplementation("com.google.truth.extensions:truth-liteproto-extension:1.4.5")
  testImplementation("org.robolectric:robolectric:4.17")
  testImplementation("androidx.work:work-testing:2.12.0")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.36.2" }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        register("java") { option("lite") }
        register("kotlin") { option("lite") }
      }
    }
  }
}

abstract class GenerateHealthPermissionsManifestTask : DefaultTask() {
  @get:Input abstract val dataTypes: ListProperty<String>

  @get:OutputFile abstract val outputManifest: RegularFileProperty

  @TaskAction
  fun generate() {
    val permissions =
      dataTypes.get().joinToString("\n") { type ->
        """  <uses-permission android:name="android.permission.health.READ_$type" />"""
      }
    outputManifest
      .get()
      .asFile
      .writeText(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
          "$permissions\n" +
          "</manifest>\n"
      )
  }
}

val supportedDataTypes =
  listOf(
    // keep-sorted start
    "ACTIVE_CALORIES_BURNED",
    "ACTIVITY_INTENSITY",
    "BLOOD_GLUCOSE",
    "BLOOD_PRESSURE",
    "BODY_FAT",
    "DISTANCE",
    "ELEVATION_GAINED",
    "EXERCISE",
    "FLOORS_CLIMBED",
    "HEART_RATE",
    "HEART_RATE_VARIABILITY",
    "HEIGHT",
    "HYDRATION",
    "MINDFULNESS",
    "NUTRITION",
    "OXYGEN_SATURATION",
    "RESPIRATORY_RATE",
    "RESTING_HEART_RATE",
    "SKIN_TEMPERATURE",
    "SLEEP",
    "STEPS",
    "TOTAL_CALORIES_BURNED",
    "VO2_MAX",
    "WEIGHT",
    // keep-sorted end
  )

androidComponents {
  onVariants { variant ->
    val taskName =
      "generate${variant.name.replaceFirstChar { it.uppercase() }}HealthPermissionsManifest"
    val generateManifestTask =
      tasks.register<GenerateHealthPermissionsManifestTask>(taskName) {
        dataTypes.set(supportedDataTypes)
        outputManifest.set(
          layout.buildDirectory.file("generated/manifests/$name/AndroidManifest.xml")
        )
      }
    variant.sources.manifests.addGeneratedManifestFile(
      generateManifestTask,
      GenerateHealthPermissionsManifestTask::outputManifest,
    )
  }
}
