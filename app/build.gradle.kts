plugins {
    id("com.android.application")
}

android {
    namespace = "com.timebox.guard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timebox.guard"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.10-debug"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
