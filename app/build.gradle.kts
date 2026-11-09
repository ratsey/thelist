import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// gradle.properties holds the only version number in the project. versionCode is derived from
// it so a release never needs two numbers kept in step: major * 10000 + minor * 100 + patch.
val appVersionName = (findProperty("appVersionName") as String?) ?: "0.1.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: run {
    val parts = appVersionName.split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 10_000 + minor * 100 + patch
}
val githubOwner = (findProperty("githubOwner") as String?) ?: "your-github-username"
val githubRepo = (findProperty("githubRepo") as String?) ?: "trolley"

// Signing credentials come from env vars (CI) or an untracked keystore.properties (local).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun credential(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

val keystorePath = credential("KEYSTORE_PATH")
val releaseKeystoreFile = keystorePath?.let { path ->
    val file = File(path)
    if (file.isAbsolute) file else rootProject.file(path)
}
val hasReleaseKeystore = releaseKeystoreFile?.exists() == true

android {
    namespace = "com.ratsey.thelist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ratsey.thelist"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("String", "RELEASES_URL", "\"https://github.com/$githubOwner/$githubRepo/releases\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = credential("KEYSTORE_PASSWORD")
                keyAlias = credential("KEY_ALIAS")
                keyPassword = credential("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // On-device receipt OCR: bundled Latin model, no Play Services download.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    // The android.jar stub throws on org.json calls; unit tests need the real implementation.
    testImplementation(libs.org.json)
}
