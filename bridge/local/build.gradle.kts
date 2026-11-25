plugins { alias(libs.plugins.android.library) }

android {
  namespace = "com.inscopexlabs.gitana.gitbridge.local"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":bridge:contracts"))
  implementation(project(":bridge:core"))
  testImplementation(libs.junit)
}
