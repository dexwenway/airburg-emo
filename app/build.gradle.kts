plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is supplied only through environment variables so that no
// keystore path or password ever lives in the repository. When they are absent,
// assembleRelease produces an unsigned APK instead of silently using debug keys.
val releaseKeyStore = providers.environmentVariable("EMO_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("EMO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("EMO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("EMO_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseKeyStore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "dev.amor.emomonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.amor.emomonitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeyStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Never fall back to the debug key: unsigned output is intentional when
            // EMO_RELEASE_* variables are not set.
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                logger.lifecycle(
                    "WARNING: EMO_RELEASE_* env vars not set; assembleRelease produces an UNSIGNED APK.",
                )
            }
        }
    }

    androidResources {
        localeFilters += listOf("zh", "en")
    }

    packaging {
        resources {
            // Keep every dependency's own license/notice text in the APK. The
            // previous "META-INF/**/LICENSE.txt" exclude silently dropped all
            // of them; AndroidX ships them at unique per-artifact paths, so
            // they can coexist. If two artifacts ever share a path, merge the
            // texts instead of failing the build or discarding a license.
            merges += listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/**/LICENSE",
                "META-INF/**/LICENSE.txt",
                "META-INF/**/NOTICE",
                "META-INF/**/NOTICE.txt",
            )
            excludes += listOf(
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
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

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}
