plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.inscopexlabs.gitana.gitbridge"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.inscopexlabs.gitana.gitbridge"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  // TODO: release signing is intentionally not configured yet.
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { compose = true }
}

dependencies {
  implementation(project(":bridge:contracts"))
  implementation(project(":bridge:core"))
  implementation(project(":bridge:github"))
  implementation(project(":bridge:vault"))
  implementation(project(":bridge:storage"))
  implementation(project(":bridge:local"))
  implementation(project(":bridge:relay"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
}
