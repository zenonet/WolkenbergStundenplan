import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

val crashReportEndpoint: String = gradleLocalProperties(rootDir, providers).getProperty("crashreport.endpoint", "\"https://example.com\"")
val devEmail: String = gradleLocalProperties(rootDir, providers).getProperty("dev.email", "\"about:blank\"")

android {
    namespace = "de.zenonet.stundenplan"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.zenonet.stundenplan"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "1.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "errorReportUrl", crashReportEndpoint)
        buildConfigField("String", "devEmail", devEmail)
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
    }
    baselineProfile{
        dexLayoutOptimization = true
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.wear:wear-remote-interactions:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose-android:2.9.2")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // implementation ("com.android.support:appcompat-v7:23.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("androidx.work:work-runtime:2.10.3")

    // Viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")

    implementation(project(":Common"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    "baselineProfile"(project(":baselineprofile"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    implementation("me.zhanghai.compose.preference:library:1.0.0")
    implementation("com.google.accompanist:accompanist-permissions:0.31.0-alpha")

    // Glance app widgets
    implementation( "androidx.glance:glance-appwidget:1.0.0" )
    implementation( "androidx.glance:glance-material3:1.0.0" )

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // In-App reviews
    implementation("com.google.android.play:review:2.0.1")
    implementation("com.google.android.play:review-ktx:2.0.1")


    //baseline profile installer
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}