// RBSB-Weather for Android. Created By Shane BlackbuRn
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.shanebl030glitch.rbsbweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.shanebl030glitch.rbsbweather"
        minSdk = 29                     // Android 10 and newer
        targetSdk = 35
        versionCode = 14
        versionName = "0.7.2"
    }

    signingConfigs {
        // Test builds are signed with a test key kept in the repo, so each new test build
        // installs over the last one. That key is public: it never signs a release.
        create("test") {
            storeFile = rootProject.file("keystore/rbsb-test.keystore")
            storePassword = "rbsbtest"
            keyAlias = "rbsbtest"
            keyPassword = "rbsbtest"
        }
        // Releases (from 0.7.2) are signed with Shane's own release key. The key and its
        // passwords are never in the repository: the build gets them from environment
        // variables, set by the Release workflow from GitHub's encrypted secrets, or on
        // Shane's own computer. See README: "Signing".
        val releaseKeystore = System.getenv("RBSB_RELEASE_KEYSTORE")
        if (!releaseKeystore.isNullOrBlank()) create("production") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("RBSB_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RBSB_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RBSB_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("test")
        }
        getByName("release") {
            isMinifyEnabled = false
            // the release key when it's given; otherwise this is a test build (test key)
            signingConfig = signingConfigs.findByName("production") ?: signingConfigs.getByName("test")
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")         // background checks for warnings and quakes

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")          // the real org.json for JVM tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
