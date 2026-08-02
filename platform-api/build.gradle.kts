plugins {
    // AGP 9 provides built-in Kotlin support, so the library applies only the
    // Android plugin (the app module does the same) — applying kotlin.android
    // on top double-registers the `kotlin` extension.
    alias(libs.plugins.android.library)
}

/**
 * :platform-api (Change 2) — the tiny set of device interfaces the app needs
 * for QR and OCR. It exists to make the DI boundary **physical**: `:parser-core`
 * stays Android-free, and no proprietary engine (ML Kit / ZXing / Tesseract)
 * may appear here or in the core — only implementations in the app's flavor
 * source sets satisfy these interfaces. See ARCHITECTURE.md.
 *
 * 8a ships the interfaces + the DI seam only; implementations arrive with the
 * gms/foss flavors (8b).
 */
android {
    namespace = "com.racunko.platform"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
