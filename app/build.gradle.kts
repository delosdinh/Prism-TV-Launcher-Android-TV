plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.prismtv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prismtv.launcher"
        minSdk = 24
        targetSdk = 35
        // CI passes a version per build so Software Update can tell builds apart.
        versionCode = (project.findProperty("prismVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("prismVersionName") as String?) ?: "0.1.0"
    }

    // A permanent signing key (from CI secrets) lets each new build install over the last one.
    // Without it, builds fall back to the machine's debug key and can't update each other.
    val prismKeystore = System.getenv("PRISM_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (prismKeystore != null) {
            create("prism") {
                storeFile = prismKeystore
                storePassword = System.getenv("PRISM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PRISM_KEY_ALIAS") ?: "prism"
                keyPassword = System.getenv("PRISM_KEY_PASSWORD") ?: System.getenv("PRISM_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (prismKeystore != null) signingConfig = signingConfigs.getByName("prism")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (prismKeystore != null) "prism" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Reports only (CI's "Lint and unit tests" job publishes them): findings never fail a build.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
        xmlReport = true
        // Style hints only (SharedPreferences.edit { }, String.toUri()): the plain calls do the same.
        disable += "UseKtx"
    }

    // JVM unit tests (app/src/test): Robolectric gives them real resources and SharedPreferences.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // Navigation sounds are lossless FLAC (a seventh the size of WAV). SoundPool opens them straight from
    // the APK, which only works for files stored uncompressed (FLAC isn't on the build's own list).
    androidResources {
        noCompress += "flac"
    }
}

// The startup profile recorded on a device by :baselineprofile (src/release/generated/baselineProfiles),
// added to the hand-written rules in src/main/baseline-prof.txt. It's only re-recorded on request (a
// commit with [profile] in its message runs .github/workflows/baseline-profile.yml), never in a build.
baselineProfile {
    automaticGenerationDuringBuild = false
    // Startup code first in the APK's dex files, so a cold start reads less.
    dexLayoutOptimization = true
}

// Robolectric's Android framework jar comes through Gradle (and its cache) rather than being fetched by
// Robolectric while tests run: Maven Central rate-limits those downloads on CI runners (HTTP 429).
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val syncRobolectricJars by tasks.registering(Sync::class) {
    from(robolectricRuntime)
    into(robolectricJarsDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
}

dependencies {
    "baselineProfile"(project(":baselineprofile"))
    robolectricRuntime(libs.robolectric.android14)
    robolectricRuntime(libs.robolectric.android15)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Prism Keyboard (an input method) hosts Compose outside an activity.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Installs the baseline profile (src/main/baseline-prof.txt) so Prism TV's code is compiled ahead of time.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.media3.exoplayer)
    // Optional: lets Shizuku users close apps completely (force stop) from the App Switcher.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
}
