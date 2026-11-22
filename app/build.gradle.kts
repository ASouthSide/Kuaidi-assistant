plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kuaidi.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kuaidi.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "2.0.1"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
        }
        create("store") {
            dimension = "distribution"
            applicationIdSuffix = ".store"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    val bundledTestLibraries = providers.gradleProperty("bundledTestLibraries").orNull
    if (bundledTestLibraries == null) {
        testImplementation("junit:junit:4.13.2")
    } else {
        testImplementation(files(
            "$bundledTestLibraries/junit-4.13.2.jar",
            "$bundledTestLibraries/hamcrest-core-1.3.jar"
        ))
    }
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // 本地离线中文 OCR（图片识别快递）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
