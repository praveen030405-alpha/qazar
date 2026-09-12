plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.qazar.pdfviewer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.qazar.pdfviewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "meridian123"
            keyAlias = "qazar_release"
            keyPassword = "meridian123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        useLegacyPackaging = true
      }
    }

    testOptions {
      unitTests.isReturnDefaultValues = true
    }
}

// --- Auto-rebuild Rust native library before every build ---
val ndkRoot = System.getenv("ANDROID_NDK_HOME")
    ?: "${System.getenv("ANDROID_SDK_ROOT") ?: "${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk"}\\ndk\\27.3.13750724"
val toolchainBin = "$ndkRoot\\toolchains\\llvm\\prebuilt\\windows-x86_64\\bin"
val projectRoot = rootProject.projectDir.parentFile.absolutePath

tasks.register<Exec>("buildRustBridge") {
    description = "Cross-compiles the Rust meridian-bridge .so for arm64-v8a"
    workingDir = file(projectRoot)
    environment("CC_aarch64_linux_android", "$toolchainBin\\aarch64-linux-android35-clang.cmd")
    environment("AR_aarch64_linux_android", "$toolchainBin\\llvm-ar.exe")
    environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", "$toolchainBin\\aarch64-linux-android35-clang.cmd")
    commandLine("cargo", "rustc", "--target-dir", "target-android", "--target", "aarch64-linux-android", "-p", "meridian-bridge", "--", "--crate-type", "cdylib")
    doLast {
        val soSrc = file("$projectRoot/target-android/aarch64-linux-android/debug/libmeridian_bridge.so")
        val soDst = file("src/main/jniLibs/arm64-v8a/libmeridian.so")
        if (soSrc.exists()) {
            soSrc.copyTo(soDst, overwrite = true)
            println("âœ… Copied fresh libmeridian_bridge.so to jniLibs")
        } else {
            throw GradleException("â Œ Rust build succeeded but .so not found at: ${soSrc.absolutePath}")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("buildRustBridge")
}


kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui-text-google-fonts")

  // CameraX for Q-Scan Custom Camera Engine
  val cameraxVersion = "1.4.1"
  implementation("androidx.camera:camera-camera2:$cameraxVersion")
  implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
  implementation("androidx.camera:camera-view:$cameraxVersion")

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
}
