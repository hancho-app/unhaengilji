import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

// 서명 값은 저장소에 두지 않는다. 없으면 release 빌드도 debug 키로 서명해 에뮬레이터에서 돈다.
val keyProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

android {
  namespace = "app.unhaengilji"
  compileSdk = 35
  useLibrary("android.car")

  defaultConfig {
    applicationId = "app.unhaengilji"
    minSdk = 28
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }

  signingConfigs {
    if (keyProps.isNotEmpty()) {
      create("release") {
        storeFile = rootProject.file(keyProps.getProperty("storeFile"))
        storePassword = keyProps.getProperty("storePassword")
        keyAlias = keyProps.getProperty("keyAlias")
        keyPassword = keyProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = if (keyProps.isNotEmpty()) signingConfigs.getByName("release")
                      else signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation("ai.pleos.playground:Vehicle:2.3.2")
  implementation("ai.pleos.playground:NaviHelper:3.0.5")
  implementation("com.google.zxing:core:3.5.3")
  testImplementation("junit:junit:4.13.2")
}
