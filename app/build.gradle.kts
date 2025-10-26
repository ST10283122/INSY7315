import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.insy_7315"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.insy_7315"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            val properties = Properties()
            properties.load(FileInputStream(rootProject.file("local.properties")))
            buildConfigField("String", "DB_CONNECTION_STRING", "\"${properties.getProperty("DB_CONNECTION_STRING")}\"")
        }
        release {
            isMinifyEnabled = false
            val properties = Properties()
            properties.load(FileInputStream(rootProject.file("local.properties")))
            buildConfigField("String", "DB_CONNECTION_STRING", "\"${properties.getProperty("DB_CONNECTION_STRING")}\"")
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.1.jre11")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}