import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.api.DefaultTask // Import needed for tasks.register<DefaultTask>

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.owasp.dependencycheck") version "9.0.9"
    id("org.sonarqube") version "4.4.1.3373"
    id("jacoco")
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

        // Load local.properties
        val properties = Properties()
        // Ensure file input stream is closed
        FileInputStream(rootProject.file("local.properties")).use {
            properties.load(it)
        }
        buildConfigField(
            "String",
            "DB_CONNECTION_STRING",
            "\"${properties.getProperty("DB_CONNECTION_STRING")}\""
        )

        testOptions.unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
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

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

// OWASP Dependency Check (no suppression file)
dependencyCheck {
    analyzers.assemblyEnabled = false
    format = "HTML"
    failBuildOnCVSS = 7.0f

    // Using addAll() to correctly set the List<String> property
    scanConfigurations.addAll(listOf("implementation", "compile", "runtimeClasspath"))
}

// SonarQube
sonarqube {
    properties {
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "https://sonarcloud.io")
        property("sonar.login", System.getenv("SONAR_TOKEN") ?: "")
        property("sonar.projectKey", "ST10283122_INSY7315")
        property("sonar.projectName", "INSY7315 Android Project")

        // Using layout.buildDirectory for modern Gradle APIs
        property("sonar.coverage.jacoco.xmlReportPaths", layout.buildDirectory.file("reports/jacoco/testDebugUnitTestCoverage/testDebugUnitTestCoverage.xml").get().asFile.absolutePath)
        property("sonar.java.coveragePlugin", "jacoco")
        property("sonar.junit.reportPaths", layout.buildDirectory.dir("test-results/testDebugUnitTest").get().asFile.absolutePath)
        property("sonar.androidLint.reportPaths", layout.buildDirectory.file("reports/lint-results.xml").get().asFile.absolutePath)

        property(
            "sonar.coverage.exclusions",
            "**/R.class,**/BR.class,**/BuildConfig.*,**/Manifest*.*,**/*Test*.*,**/android/**/*.*"
        )
        property("sonar.tests", "src/test/java")
        property("sonar.sourceEncoding", "UTF-8")
        // Added to address the SonarQube deprecation warning
        property("sonar.gradle.skipCompile", "true")
    }
}

// Jacoco
jacoco {
    toolVersion = "0.8.8"
}

// Jacoco coverage task
tasks.register<JacocoReport>("testDebugUnitTestCoverage") {
    group = "Reporting"
    description = "Generate Jacoco coverage reports for Debug unit tests"

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/BR.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/android/**/*.*",
        "**/androidx/**/*.*",
        "**/*\$*.*"
    )

    // Using layout.buildDirectory to fix buildDir deprecation warning
    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(fileFilter)
    }
    // Using layout.buildDirectory to fix buildDir deprecation warning
    val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(javaClasses, kotlinClasses))

    // Using layout.buildDirectory to fix buildDir deprecation warning
    val jacocoExecFiles: FileCollection = fileTree(layout.buildDirectory.asFile) {
        include("jacoco/testDebugUnitTest.exec")
    }
    executionData.setFrom(jacocoExecFiles)

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    // FIX 4: Removed hardcoded dependsOn("testDebugUnitTest") here.
    // It will be added in the afterEvaluate block below for correctness.
}

// Wrapper task to run Jacoco coverage
tasks.register<DefaultTask>("testCoverageReport") {
    group = "Reporting"
    description = "Generate test coverage report"
    // FIX 5: Removed hardcoded dependsOn("testDebugUnitTest") here.
    // It will be added in the afterEvaluate block below for correctness.
    dependsOn("testDebugUnitTestCoverage") // Keep the dependency on the report task
}

// FIX 6: Use afterEvaluate to ensure AGP tasks like testDebugUnitTest exist before referencing them
afterEvaluate {
    // This block runs after the Android plugin has created all the necessary tasks.

    // Line 207 fix: Ensure Jacoco report runs after unit tests
    tasks.named<Test>("testDebugUnitTest") {
        finalizedBy("testDebugUnitTestCoverage")
    }

    // Fix dependency on testDebugUnitTest for the JaCoCo report task
    tasks.named("testDebugUnitTestCoverage") {
        dependsOn("testDebugUnitTest")
    }

    // Fix dependency for the wrapper task
    tasks.named("testCoverageReport") {
        dependsOn("testDebugUnitTest")
    }
}

// Ensure dependency check runs after assemble
tasks.named("dependencyCheckAnalyze") {
    dependsOn("assembleDebug")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.1.jre11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Unit testing
    testImplementation(libs.junit)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:4.10.3")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("androidx.annotation:annotation:1.7.0")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.8")

    // Android instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
}