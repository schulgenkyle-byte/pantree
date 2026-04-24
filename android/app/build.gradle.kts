plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "app.pantrie"
  compileSdk = 35

  defaultConfig {
    applicationId = "app.pantrie"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
    vectorDrawables.useSupportLibrary = true

    buildConfigField("String", "API_BASE_URL",
      "\"${project.findProperty("PANTRIE_API_URL") ?: "https://pantrie-backend.REPLACE.workers.dev"}\"")
    buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID",
      "\"${project.findProperty("PANTRIE_GOOGLE_CLIENT_ID") ?: "REPLACE.apps.googleusercontent.com"}\"")
    // SHA-256 SPKI pins (base64, no "sha256/" prefix). Leave blank in debug; required in release.
    // See android/app/src/main/res/xml/network_security_config.xml for how to generate them.
    buildConfigField("String", "API_PIN_PRIMARY",
      "\"${project.findProperty("PANTRIE_API_PIN_PRIMARY") ?: ""}\"")
    buildConfigField("String", "API_PIN_BACKUP",
      "\"${project.findProperty("PANTRIE_API_PIN_BACKUP") ?: ""}\"")
    buildConfigField("Long", "CLOUD_PROJECT_NUMBER",
      "${project.findProperty("PANTRIE_CLOUD_PROJECT_NUMBER") ?: "0L"}")
    buildConfigField("String", "DEV_TOKEN_KEY",
      "\"${project.findProperty("PANTRIE_DEV_TOKEN_KEY") ?: ""}\"")
  }

  signingConfigs {
    create("release") {
      val ksf = System.getenv("RELEASE_KEYSTORE")
      if (ksf != null) {
        storeFile = file(ksf)
        storePassword = System.getenv("RELEASE_KEYSTORE_PW")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PW")
      }
    }
  }

  buildTypes {
    debug {
      isDebuggable = true
      applicationIdSuffix = ".dev"
      versionNameSuffix = "-dev"
    }
    release {
      isDebuggable = false
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons)
  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.sqlcipher.android)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.biometric)

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.google.id)

  implementation(libs.play.billing)
  implementation(libs.play.integrity)

  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  implementation(libs.retrofit)
  implementation(libs.retrofit.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)

  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.kotlinx.coroutines.play.services)

  implementation("androidx.core:core-splashscreen:1.0.1")
}
