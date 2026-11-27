import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The version is major.minor.build. The first two parts are set in gradle.properties; the build
// number is the git commit count, so every commit is already a new version and there is no number
// to remember. versionCode is derived from all three, never stored, so it cannot drift.
val versionMajor = (findProperty("appVersionMajor") as String?)?.toIntOrNull() ?: 0
val versionMinor = (findProperty("appVersionMinor") as String?)?.toIntOrNull() ?: 0
val versionBuildFallback = (findProperty("appVersionBuild") as String?)?.toIntOrNull() ?: 0

/** `git rev-list --count HEAD`, or null when this tree has no usable git history. */
fun gitCommitCount(): Int? = runCatching {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) text.toIntOrNull() else null
}.getOrNull()

val versionBuild = gitCommitCount()?.takeIf { it > 0 } ?: versionBuildFallback
require(versionBuild < 10_000) {
    "Build $versionBuild does not fit the versionCode layout; bump appVersionMinor and start again."
}
val appVersionName = "$versionMajor.$versionMinor.$versionBuild"
val appVersionCode = versionMajor * 1_000_000 + versionMinor * 10_000 + versionBuild
val githubOwner = (findProperty("githubOwner") as String?) ?: "your-github-username"
val githubRepo = (findProperty("githubRepo") as String?) ?: "thelist"
// Left empty until the relay in deploy/kamatera is live; the share dialog then asks for a URL.
val syncServerUrl = (findProperty("syncServerUrl") as String?) ?: ""

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

        // Phones, not four architectures: the on-device OCR pipeline is 11 MB per ABI and this app
        // ships its own updates, so the emulator-only x86 builds would cost every phone 33 MB of
        // download for nothing. Both ARM widths stay so a 32-bit handset is not left behind.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("String", "RELEASES_URL", "\"https://github.com/$githubOwner/$githubRepo/releases\"")
        buildConfigField("String", "SYNC_SERVER_URL", "\"$syncServerUrl\"")
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

    testOptions {
        // Room is tested on the JVM under Robolectric, which needs the merged manifest and the
        // resources there is no device to ask for.
        unitTests.isIncludeAndroidResources = true
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

    // Scan to add: the camera feeds the on-device barcode model; nothing leaves the phone
    // except the digits it read, which go to the product database for a name and a size.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    // Shelf labels carry a price a barcode cannot: this reads the figures the tag prints, on the
    // phone, so a price can be picked up in the aisle without a retailer's API or a network call.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    // The android.jar stub throws on org.json calls; unit tests need the real implementation.
    testImplementation(libs.org.json)
    // An in-memory database on the JVM: a repository path that touches SQL is exercised, not just
    // compiled, and the suite stays runnable where there is no emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
