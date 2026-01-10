plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.resq.resq_sos_mientrung_android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.resq.resq_sos_mientrung_android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Fix for AAR metadata issues
        multiDexEnabled = true
        
        // Support for 16 KB page size devices (Android 15+)
        // This helps with compatibility warning for native libraries
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
        
        // Note: 16 KB page size warning is from Bridgefy SDK's native libraries
        // This is a known issue with libsignal_jni.so
        // The warning can be ignored for now, but should be fixed by Bridgefy SDK update
        // App will still work on most devices (only affects some Android 15+ devices with 16KB pages)
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.multidex)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    coreLibraryDesugaring(libs.androidx.desugar.jdk.libs)
    
    // Bridgefy SDK
    // Option 1: From Maven repository (if available)
    implementation(group = "me.bridgefy", name = "android-sdk", version = "1.2.3", ext = "aar") {
        isTransitive = true
    }
    
    // Signal Protocol is included as transitive dependency of Bridgefy SDK
    
    // Option 2: From local libs folder (uncomment if you have the AAR file)
    // Uncomment the line below and place bridgefy-sdk.aar in app/libs/ folder
    // implementation(files("libs/bridgefy-sdk.aar"))
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}