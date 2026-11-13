plugins { id("com.android.application") }

// Release signing comes from the environment (the release workflow decodes the keystore from a
// secret), so the key never enters the repository. Without it the release build is left unsigned,
// which is what F-Droid builds from.
val releaseKeystore: String? = System.getenv("DENGJEN_KEYSTORE")
val missingSigningVars: List<String> =
    if (releaseKeystore == null) emptyList()
    else listOf("DENGJEN_KEYSTORE_PASSWORD", "DENGJEN_KEY_ALIAS", "DENGJEN_KEY_PASSWORD")
        .filter { System.getenv(it).isNullOrEmpty() }

android {
    namespace = "io.github.zirekhq.dengjen"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.zirekhq.dengjen"
        // The dengjen AARs need API 27.
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // The AARs ship only these ABIs (32-bit ARM is ZirekHQ/dengjen#542).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("DENGJEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DENGJEN_KEY_ALIAS")
                keyPassword = System.getenv("DENGJEN_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
    // The dependency metadata block is an encrypted blob only Google can read; F-Droid rejects it
    // in APKs. Play still gets it through the bundle.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        checkDependencies = false
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Checked once the task graph is known and before any task runs, so nothing gets packaged or signed
// first, and Gradle sync (which schedules no release task) still works. Play rejects an unsigned
// bundle, so bundleRelease always needs the key; assembleRelease may stay unsigned for F-Droid, but a
// half-set key fails here with a clear message.
gradle.taskGraph.whenReady {
    val bundling = hasTask("${project.path}:bundleRelease")
    val releasing = bundling || hasTask("${project.path}:assembleRelease")
    if (releasing && missingSigningVars.isNotEmpty()) {
        throw GradleException("DENGJEN_KEYSTORE is set, but these are missing: ${missingSigningVars.joinToString()}; see RELEASING.md")
    }
    if (bundling && releaseKeystore == null) {
        throw GradleException("bundleRelease needs DENGJEN_KEYSTORE and the key variables; see RELEASING.md")
    }
}

dependencies {
    // Fetched by scripts/fetch-aars.sh from the release pinned in dengjen.lock.
    implementation(files("../libs/dengjen-android-core.aar", "../libs/dengjen-android-tts.aar"))
    testImplementation("junit:junit:4.13.2")
    // android.jar stubs org.json in unit tests; this supplies a working copy.
    testImplementation("org.json:json:20260814")
}
