import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Pull API base + token from local.properties so they don't end up in git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val shelfApiBase: String = localProps.getProperty("shelf.api.base") ?: "https://media.kzaller.com"
val shelfApiToken: String = localProps.getProperty("shelf.api.token") ?: ""
// Google Web OAuth client id (the token audience). Not secret; safe to default in-source so a
// build works without extra config, overridable via local.properties / env.
val googleWebClientId: String = localProps.getProperty("google.web.client.id")
    ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
    ?: "262141921179-e2e2c1va41qp7vq9kv1qn2p4b8r2igrl.apps.googleusercontent.com"

// Release signing: read from local.properties locally, or env vars in CI. Either may be absent;
// if all four are missing we fall back to no signing config and `assembleRelease` warns.
val keystorePath: String? = localProps.getProperty("keystore.path") ?: System.getenv("KEYSTORE_PATH")
val keystorePassword: String? = localProps.getProperty("keystore.password") ?: System.getenv("KEYSTORE_PASSWORD")
val signingKeyAlias: String? = localProps.getProperty("key.alias") ?: System.getenv("KEY_ALIAS")
val signingKeyPassword: String? = localProps.getProperty("key.password") ?: System.getenv("KEY_PASSWORD")
val signingConfigured: Boolean = keystorePath != null && keystorePassword != null &&
        signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "com.kzaller.shelf"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kzaller.shelf"
        minSdk = 26
        targetSdk = 34
        // CI passes its run number so every build supersedes the last; Android refuses to
        // install over a newer versionCode, and this sat at 1 for a hundred-odd builds.
        versionCode = 1 + ((System.getenv("BUILD_NUMBER") ?: "0").toIntOrNull() ?: 0)
        versionName = "1.0"

        buildConfigField("String", "API_BASE", "\"$shelfApiBase\"")
        buildConfigField("String", "API_TOKEN", "\"$shelfApiToken\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        // Define the 'release' config only when all credentials are available; creating
        // it with nulls throws at evaluation time.
        if (signingConfigured) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Shrink and obfuscate. The bulk of the APK was unused library code -- most of it
            // material-icons-extended, which ships every icon Material has and of which this app
            // uses a couple of dozen. Resource shrinking then drops the drawables and strings
            // those removed classes were the only reference to.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("media-shelf: release build will be UNSIGNED -- set keystore.* in local.properties (or KEYSTORE_* env vars) to sign.")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)
}
