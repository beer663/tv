plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.tvbrowser"
    compileSdk = 34
    defaultConfig { applicationId = "com.example.tvbrowser"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.mozilla.geckoview:${project.property("geckoArtifact")}:${project.property("geckoVersion")}")
}
