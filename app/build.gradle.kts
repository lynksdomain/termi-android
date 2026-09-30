plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.termi.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.termi.android"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStore = providers.environmentVariable("TERMI_KEYSTORE_PATH").orNull
    val releasePassword = providers.environmentVariable("TERMI_KEYSTORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("TERMI_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("TERMI_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(releaseStore, releasePassword, releaseAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }
    require(listOf(releaseStore, releasePassword, releaseAlias, releaseKeyPassword)
        .all { it.isNullOrBlank() } || hasReleaseSigning) { "Supply all four TERMI signing variables, or none." }
    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(releaseStore))
            storePassword = releasePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    // Embedded local HTTP server for WiFi Transfer (mirrors iOS's
    // NWListener-based WiFiTransferServer) — single-jar, no transitive deps.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Settings persistence (mirrors iOS's UserDefaults-backed AppSettings).
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
