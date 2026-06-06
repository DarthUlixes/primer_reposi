plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.primer_repositorio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.primer_repositorio"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-chat"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
}
