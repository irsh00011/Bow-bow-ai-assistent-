// Top of file: explicit imports — in a Kotlin DSL build script the
// bare name `java` resolves to the Java plugin extension, not the
// java.* packages, so java.util / java.io need importing.
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.irshad.bowbow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.irshad.bowbow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing reads keystore.properties (git-ignored, never
    // committed). Without it the release build is unsigned.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        keystoreProps.load(FileInputStream(keystorePropsFile))
    }
    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                keyAlias = keystoreProps.getProperty("keyAlias")
                    ?: error("keyAlias missing in keystore.properties")
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: error("keyPassword missing in keystore.properties")
                storeFile = file(
                    keystoreProps.getProperty("storeFile")
                        ?: error("storeFile missing in keystore.properties")
                )
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: error("storePassword missing in keystore.properties")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
