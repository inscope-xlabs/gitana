plugins { alias(libs.plugins.android.library) }

android {
  namespace = "com.inscopexlabs.gitana.gitbridge.local"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":bridge:contracts"))
  implementation(project(":bridge:core"))
  api(libs.kotlinx.coroutines.core)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.body.limit)
  testImplementation(libs.junit)
}

// Print assertion messages in the build log, so socket test failures can be diagnosed from CI.
tasks.withType<Test>().configureEach {
  testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
