plugins {
  id("com.android.application") version "9.3.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
  id("com.google.protobuf") version "0.10.0" apply false
}

buildscript {
  repositories {
    google()
    mavenCentral()
  }
}
