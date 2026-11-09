plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.freedomdiffusion.app"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.freedomdiffusion.app"
    minSdk = 29
    targetSdk = 35
    versionCode = 5
    versionName = "0.5.0-alpha"
    ndk { abiFilters += listOf("arm64-v8a") }
  }
  packaging { jniLibs { useLegacyPackaging = true } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
