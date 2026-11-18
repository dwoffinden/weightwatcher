plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  id("com.google.protobuf")
  id("io.github.takahirom.roborazzi")
}

val widgetRenderer: String = providers.gradleProperty("widgetRenderer").orNull ?: "auto"

require(widgetRenderer in setOf("auto", "glance")) {
  "Unsupported -PwidgetRenderer=$widgetRenderer (expected 'auto' or 'glance')"
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
    buildConfigField("boolean", "ENABLE_REMOTE_COMPOSE", (widgetRenderer == "auto").toString())
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
    buildConfig = true
    compose = true
  }

  kotlin { jvmToolchain(25) }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric's FileDescriptorInterceptor reflects on jdk.internal.access.SharedSecrets when
      // ApplicationSharedMemory initializes on SDK 36+
      // (https://github.com/robolectric/robolectric/issues/11434).
      all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
  }
}

roborazzi { outputDir.set(file("src/test/snapshots")) }

dependencies {
  implementation("androidx.core:core-ktx:1.19.1")
  implementation("com.google.guava:guava:33.8.0-android")

  // Compose
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")

  // RemoteCompose
  implementation("androidx.compose.remote:remote-creation:1.0.0-alpha23")
  implementation("androidx.compose.remote:remote-creation-compose:1.0.0-alpha23")
  implementation("androidx.compose.remote:remote-creation-core:1.0.0-alpha23")
  implementation("androidx.compose.remote.foundation:foundation:1.0.0-alpha06")

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
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.79.0")
  testImplementation("androidx.compose.remote:remote-player-core:1.0.0-alpha23")
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
