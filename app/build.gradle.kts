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

    // Two editions from one codebase (docs/editions.md):
    //  - github: the APK for you and friends. Software Update, Shizuku/root/adb helpers, Pro unlocked.
    //  - play:   the bundle for Google Play. No self-updater or shell helpers; Prism Pro through Play Billing.
    // Both are signed with the same permanent key. The Play edition has its own package name, so the two can
    // sit side by side on one TV and Google Play never replaces a GitHub install (or the other way round).
    // A package name is permanent on Google Play once the first bundle is uploaded: decide before that.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "PLAY", "false")
            // Where Software Update looks: a public releases-only repository, so the code repository can be
            // private (docs/editions.md). Override with -PprismUpdateRepo=owner/name.
            val updateRepo = (project.findProperty("prismUpdateRepo") as String?) ?: "delosdinh/Prism-TV-Launcher-Releases"
            buildConfigField("String", "UPDATE_URL", "\"https://github.com/$updateRepo/releases/download/preview\"")
        }
        create("play") {
            dimension = "store"
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "PLAY", "true")
            buildConfigField("String", "UPDATE_URL", "\"\"")
        }
    }

    // A permanent signing key (from CI secrets) lets each new build install over the last one.
    // Without it, debug builds use the machine's debug key and can't update each other.
    val prismKeystore = System.getenv("PRISM_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    // A release build signed with a debug key must never be mistaken for the real thing, so on CI a release
    // build without the permanent key fails (see the check after this block). On your own computer, or on CI
    // with -PprismAllowDebugSignedRelease=true (pull requests from forks, which can't see the key), it's
    // signed with the debug key instead, for testing only.
    val allowDebugSignedRelease = System.getenv("CI") != "true" ||
        (project.findProperty("prismAllowDebugSignedRelease") as String?) == "true"
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
            signingConfig = when {
                prismKeystore != null -> signingConfigs.getByName("prism")
                allowDebugSignedRelease -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    // Fail at the start, with a plain reason, rather than after R8 has run for minutes.
    gradle.taskGraph.whenReady {
        if (prismKeystore != null || allowDebugSignedRelease) return@whenReady
        val release = allTasks.filter { t ->
            t.project == project && t.name.endsWith("Release") &&
                listOf("assemble", "bundle", "package", "sign").any { t.name.startsWith(it) }
        }
        if (release.isNotEmpty()) {
            throw GradleException(
                "Release build without the permanent signing key (PRISM_KEYSTORE isn't set). Refusing to sign a " +
                    "release with the debug key. Set the PRISM_KEYSTORE_B64 and PRISM_KEYSTORE_PASSWORD secrets " +
                    "(docs/release-signing.md), or pass -PprismAllowDebugSignedRelease=true for a test build."
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

// The startup profile recorded on a device by :baselineprofile (src/main/generated/baselineProfiles),
// added to the hand-written rules in src/main/baseline-prof.txt. It's only re-recorded on request (a
// commit with [profile] in its message runs .github/workflows/baseline-profile.yml), never in a build.
baselineProfile {
    automaticGenerationDuringBuild = false
    // One profile for both editions (they share all startup code), kept in src/main/generated/baselineProfiles.
    mergeIntoMain = true
    // Startup code first in the APK's dex files, so a cold start reads less.
    dexLayoutOptimization = true
}

// Robolectric's Android framework jar comes through Gradle (and its cache) rather than being fetched by
// Robolectric while tests run: Maven Central rate-limits those downloads on CI runners (HTTP 429).
// One configuration per jar: they're versions of the same artifact, which Gradle would otherwise resolve
// to just the newest.
val robolectricAndroid14: Configuration by configurations.creating { isTransitive = false }
val robolectricAndroid15: Configuration by configurations.creating { isTransitive = false }
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val syncRobolectricJars by tasks.registering(Sync::class) {
    from(robolectricAndroid14, robolectricAndroid15)
    into(robolectricJarsDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
}

dependencies {
    "baselineProfile"(project(":baselineprofile"))
    robolectricAndroid14(libs.robolectric.android14)
    robolectricAndroid15(libs.robolectric.android15)
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
    // Settings › Screensaver › Save on This TV: Home's aerials kept on the TV (already part of ExoPlayer's own dependencies).
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    // Optional: lets Shizuku users close apps completely (force stop) from the App Switcher. Compiled into
    // both editions, but the Play edition never calls it (BuildConfig.PLAY) and drops its provider, so R8
    // removes it there.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // Prism Pro, bought once through Google Play (Play edition only).
    "playImplementation"(libs.billing)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
}
