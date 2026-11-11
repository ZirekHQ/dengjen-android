plugins { id("com.android.application") }

android {
    namespace = "io.github.zirekhq.dengjen"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.zirekhq.dengjen"
        // The dengjen AARs need API 27.
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // The AARs ship only these ABIs (32-bit ARM is ZirekHQ/dengjen#542).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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

dependencies {
    // Fetched by scripts/fetch-aars.sh from the release pinned in dengjen.lock.
    implementation(files("../libs/dengjen-android-core.aar", "../libs/dengjen-android-tts.aar"))
    testImplementation("junit:junit:4.13.2")
    // android.jar stubs org.json in unit tests; this supplies a working copy.
    testImplementation("org.json:json:20260814")
}
