plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseKeystorePath = System.getenv("CALBUDGET_KEYSTORE_PATH")?.trim().orEmpty()
val releaseKeystorePassword = System.getenv("CALBUDGET_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("CALBUDGET_KEY_ALIAS")?.trim().orEmpty()
val releaseKeyPassword = System.getenv("CALBUDGET_KEY_PASSWORD").orEmpty()
val releaseSigningRequested = releaseKeystorePath.isNotEmpty()
if (releaseSigningRequested) {
    val missing = buildList {
        if (releaseKeystorePassword.isEmpty()) add("CALBUDGET_KEYSTORE_PASSWORD")
        if (releaseKeyAlias.isEmpty()) add("CALBUDGET_KEY_ALIAS")
        if (releaseKeyPassword.isEmpty()) add("CALBUDGET_KEY_PASSWORD")
    }
    if (missing.isNotEmpty()) {
        error(
            "Release signing was requested but these environment variables are empty: " +
                missing.joinToString() +
                ". Refusing to sign with a different key.",
        )
    }
    if (!file(releaseKeystorePath).isFile) {
        error("CALBUDGET_KEYSTORE_PATH is not a keystore file. Refusing to sign with a different key.")
    }
}

android {
    namespace = "com.keenin.calbudget"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.keenin.calbudget"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (releaseSigningRequested) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Emulator (x86_64) and current phones. Drops 32-bit ABIs.
            // Signed with the machine debug key. That key is not the stable update key.
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Unsigned unless CALBUDGET_KEYSTORE_* is set. Never fall back to the debug key.
            if (releaseSigningRequested) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                // Default is a phone APK. Override with -Pabi=x86_64 for an emulator.
                val requestedAbi = (project.findProperty("abi") as String?)?.trim().orEmpty()
                abiFilters.add(requestedAbi.ifEmpty { "arm64-v8a" })
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
