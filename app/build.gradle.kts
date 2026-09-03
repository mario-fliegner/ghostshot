import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// DeinWackelbild partner-key provisioning (Block 9).
//
// Debug/non-release: local.properties -> environment variable -> blank.
// Release: environment variable only -> blank. Release deliberately never reads
// `deinWackelbildPartnerKeyLocal` -- this is the safety gate preventing a developer's local
// pilot/test key from being embedded in a release APK/AAB merely because it exists in their
// local.properties. See DEINWACKELBILD_IMPLEMENTATION_PLAN_V1.md §15.
val deinWackelbildLocalProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
}
val deinWackelbildPartnerKeyLocal: String? = deinWackelbildLocalProperties.getProperty("DEINWACKELBILD_PARTNER_KEY")
val deinWackelbildPartnerKeyEnv: String? = System.getenv("DEINWACKELBILD_PARTNER_KEY")

fun escapeForBuildConfigStringLiteral(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

android {
    namespace = "com.isardomains.sameview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.isardomains.sameview"
        minSdk = 29
        targetSdk = 36
        versionCode = 100
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Debug/non-release policy: local.properties, then env var, then blank.
        buildConfigField(
            "String",
            "DEINWACKELBILD_PARTNER_KEY",
            "\"${escapeForBuildConfigStringLiteral(deinWackelbildPartnerKeyLocal ?: deinWackelbildPartnerKeyEnv ?: "")}\""
        )
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Release safety gate: environment variable only, blank otherwise. This override
            // deliberately contains no reference to `deinWackelbildPartnerKeyLocal` -- a local
            // pilot/test key in local.properties can never reach a release build.
            buildConfigField(
                "String",
                "DEINWACKELBILD_PARTNER_KEY",
                "\"${escapeForBuildConfigStringLiteral(deinWackelbildPartnerKeyEnv ?: "")}\""
            )
        }
    }
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api29") {
                    device = "Pixel 2"
                    sdkVersion = 29
                    systemImageSource = "aosp"
                }
                create("pixel2Api33") {
                    device = "Pixel 2"
                    sdkVersion = 33
                    systemImageSource = "aosp"
                }
                create("pixel2Api35") {
                    device = "Pixel 2"
                    sdkVersion = 35
                    systemImageSource = "aosp"
                }
                create("pixel2Api36") {
                    device = "Pixel 2"
                    sdkVersion = 36
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }
            groups {
                create("allPixel2Devices") {
                    targetDevices.add(localDevices["pixel2Api29"])
                    targetDevices.add(localDevices["pixel2Api33"])
                    targetDevices.add(localDevices["pixel2Api35"])
                }
            }
        }
        unitTests {
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Provides a real org.json implementation for JVM unit tests, replacing the Android SDK stub
    // that returns null for all methods (isReturnDefaultValues = true).
    testImplementation("org.json:json:20231013")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
