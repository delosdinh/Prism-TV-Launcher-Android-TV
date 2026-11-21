plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Prism Keyboard (an input method) hosts Compose outside an activity.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
