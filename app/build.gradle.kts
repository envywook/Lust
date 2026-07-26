plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("LUST_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("LUST_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("LUST_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("LUST_KEY_PASSWORD")

android {
    namespace = "com.envy.dualcorevpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.envy.dualcorevpn"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.1.14-alpha"
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"5c9fb76e8a42eb4fecba7206fa20f35f54c78585d416b233ea77fcfbd343add6\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystorePath.orNull?.let(::file)
            storePassword = releaseKeystorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation(files("libs/libv2ray.aar"))
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}


// JVM_TARGET_17_ALIGNMENT
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
