import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Framework-wide Task Contract. Pure Kotlin/JVM: no Android, transport, bridge or worker types.
plugins { alias(libs.plugins.kotlin.jvm) }

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }

dependencies {
  testImplementation(libs.junit)
}
