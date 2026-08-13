import java.io.File
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.racunko.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.racunko.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 10
        versionName = "1.6.2"

        // Size: the x86/x86_64 slices of ML Kit and Tesseract are ~35 MB of the
        // gms APK and ~16 MB of the foss one, and they exist for emulators only —
        // no phone this app has ever run on is x86. armeabi-v7a stays: minSdk 29
        // still admits 32-bit budget devices, and it is the cheaper of the two.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Only the three languages the app actually speaks. Without this every
    // AppCompat/Compose string ships in ~80 locales the UI has no words for.
    androidResources {
        localeFilters += listOf("sr", "en", "ru")

        // PDFBox ships 92 predefined CMap files (1.21 MB) and every one of them
        // is CJK — Adobe-Japan1, Adobe-GB1, Adobe-CNS1, Adobe-Korea1 and the Uni*
        // families. A Serbian uplatnica does not use them. If some PDF ever did,
        // Pipeline.extractTextWithOcrFallback already catches the failure and
        // falls back to OCR, so the worst case is slower, not broken.
        ignoreAssetsPatterns += "cmap"
    }

    packaging {
        resources {
            // PDFBox drags in BouncyCastle, whose post-quantum lookup tables are
            // 4.15 MB of .properties. They are resources, not classes, so R8 does
            // not touch them — and nothing in Računko does SIKE or Picnic.
            excludes += "/org/bouncycastle/pqc/**"
            excludes += "/org/bouncycastle/x509/CertPathReviewerMessages_*.properties"
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE*,NOTICE*}"
            excludes += "/kotlin/**"
            excludes += "/DebugProbesKt.bin"
        }
    }

    signingConfigs {
        // Signing secrets live in keystore.properties (git-ignored) next to the
        // keystore itself; neither is committed. Without them, release builds
        // fall back to the debug signature (still installable for testing).
        //   keystore.properties: storeFile=..., storePassword=..., keyAlias=..., keyPassword=...
        val propsFile = file("${rootDir}/keystore.properties")
        if (propsFile.exists()) {
            val props = Properties().apply { propsFile.inputStream().use { load(it) } }
            val storePath = props.getProperty("storeFile")
            val keystore = if (storePath != null) file("${rootDir}/$storePath") else null
            if (keystore != null && keystore.exists()) {
                create("release") {
                    storeFile = keystore
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            // R8 on, device-proven (Android 16, arm64): gms 62.4 →
            // 35.9 MB, foss 49.2 → 23.7 MB. Unshrunk, the release shipped ~32 MB
            // of dex that was almost entirely library code nothing calls.
            //
            // The first attempt at this installed and would not launch. The cause
            // was ML Kit, not any of the usual suspects — see the ML Kit block in
            // proguard-rules.pro, which is the one part of that file you must not
            // trim without a device pass behind it.
            //
            // Debug stays unminified, so a debug device pass proves NOTHING about
            // R8. Test the RELEASE APK whenever these rules or the ML Kit / PDFBox
            // / Tesseract versions change.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isCrunchPngs = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    // Change 2: engine flavors. Same platform-api interfaces, different engines,
    // selected at compile time so neither the domain nor parser-core references
    // a concrete engine.
    //   gms  → Play Store: ML Kit barcode + text-recognition (bundled) + ZXing encode
    //   foss → F-Droid:    ZXing decode + encode + Tesseract OCR
    flavorDimensions += "engine"
    productFlavors {
        create("gms") { dimension = "engine" }
        create("foss") { dimension = "engine" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// foss OCR models are BUNDLED in the APK (user choice: zero network, ever —
// foss keeps the no-INTERNET guarantee). The tessdata_fast files are fetched at
// BUILD time on the dev/CI machine into the foss assets (never at app runtime,
// never committed to git). F-Droid runs this same task from source.
val fetchTessdata = tasks.register("fetchTessdata") {
    // Locals only (no script/Project references) so the task is
    // configuration-cache compatible.
    val outDir = layout.projectDirectory.dir("src/foss/assets/tessdata").asFile
    val langs = listOf("srp", "srp_latn", "eng")
    val version = "4.1.0" // tessdata_fast tag
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        for (lang in langs) {
            val target = File(outDir, "$lang.traineddata")
            if (target.exists() && target.length() > 0L) continue
            println("fetchTessdata: downloading $lang.traineddata …")
            val url = URI(
                "https://github.com/tesseract-ocr/tessdata_fast/raw/$version/$lang.traineddata"
            ).toURL()
            url.openStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
    }
}
// Only foss bundles the models — gms stays lean and never triggers the download.
tasks.matching { it.name.startsWith("merge") && it.name.contains("Foss") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(fetchTessdata) }
// Lint (vital) also scans the foss assets dir, so it must see the same ordering,
// otherwise release builds fail Gradle's implicit-dependency validation.
tasks.matching { it.name.contains("Foss") && it.name.contains("lint", ignoreCase = true) }
    .configureEach { dependsOn(fetchTessdata) }

dependencies {
    implementation(project(":parser-core"))
    implementation(project(":platform-api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)

    // CameraX — live IPS-QR scanning (8d). Camera is a device capability, not
    // network; the no-INTERNET guarantee is unaffected.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // gms flavor engines (proprietary ML Kit stays out of main / parser-core)
    "gmsImplementation"(libs.mlkit.barcode.scanning)
    "gmsImplementation"(libs.mlkit.text.recognition)
    "gmsImplementation"(libs.kotlinx.coroutines.play.services)
    "gmsImplementation"(libs.zxing.core)

    // foss flavor engines (ZXing decode+encode; Tesseract OCR, models bundled)
    "fossImplementation"(libs.zxing.core)
    "fossImplementation"(libs.tesseract4android)

    testImplementation(libs.junit)
    "ksp"(libs.androidx.room.compiler)
}
