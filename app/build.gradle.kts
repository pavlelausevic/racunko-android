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
        versionCode = 11
        versionName = "1.7.0"

        // Size: the x86/x86_64 slices of ML Kit and Tesseract are ~35 MB of the
        // gms APK and ~16 MB of the foss one, and they exist for emulators only —
        // no phone this app has ever run on is x86. armeabi-v7a stays: minSdk 29
        // still admits 32-bit budget devices, and it is the cheaper of the two.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Only the three languages the app actually speaks. Without this every
    // AppCompat/Compose string ships in ~80 locales the UI has no words for.
    androidResources {
        // `b+sr+Latn` must be listed EXPLICITLY. Android does not fall back from
        // sr-Latn to sr — the bare `sr` qualifier means Cyrillic Serbian, and
        // showing Cyrillic to a Latin-script user is exactly what that rule
        // prevents. Leaving it out of this list made aapt drop the folder
        // entirely: the app then had only `values/` (unqualified) and
        // `values-en`, and on a sr-Latn-RS phone a QUALIFIED match on the user's
        // second locale (en-US) beat the unqualified default. The app came up in
        // English on a Serbian phone.
        localeFilters += listOf("b+sr+Latn", "sr", "en", "ru")

        // Declares which locales the app actually speaks, and — via
        // res/resources.properties — that the unqualified `values/` folder is
        // Serbian LATIN. Without that declaration a phone set to sr-Latn-RS
        // matched none of our folders and fell through to en-US.
        generateLocaleConfig = true

        // DO NOT strip PDFBox's `cmap` assets. v1.6.0 did (`ignoreAssetsPatterns
        // += "cmap"`, 1.21 MB) on the claim that all 92 files are CJK. They are
        // 94, and two of them — **Identity-H and Identity-V** — are not CJK at
        // all: they are the encoding every modern PDF uses for a subset CID font.
        // Serbian bills are full of them.
        //
        // Removing them did not fail loudly. PDFBox kept returning text, only it
        // was glyph soup — „ЈАВНО КОМУНАЛНО" came out as `jabho komyhanho`, 4500
        // characters of it. That sailed past `OcrPolicy.needsOcr`, which measures
        // LENGTH; the address matcher found nothing, payee memory filled the
        // blank from the recipient account, and an InfoStan bill was renamed onto
        // someone else's address. Cost: three releases where every InfoStan bill
        // was quietly misread. Found 2026-08-14 by comparing bills processed
        // before v1.6.0 (correct) with the same PDFs re-processed after (wrong).
        //
        // The saving was never worth it. If it is ever attempted again, exclude
        // the CJK families BY NAME and keep Identity-*, and prove it by
        // re-processing a bill with a CID-keyed font.
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
    //   gms  → Play Store: ML Kit barcode decode + ZXing encode
    //   foss → F-Droid:    ZXing decode + encode
    // OCR is Tesseract in BOTH since v1.7 — see fetchTessdata above for why.
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

// OCR models are BUNDLED in the APK — zero network, ever. The tessdata_fast
// files are fetched at BUILD time on the dev/CI machine (never at app runtime,
// never committed to git). F-Droid runs this same task from source.
//
// v1.7: BOTH flavors now, not just foss. Every bill from a public utility here
// is printed in CYRILLIC, and ML Kit's on-device recognizer is Latin-script —
// it has no Cyrillic model and cannot be given one. Tesseract has `srp`. See
// the decision register in the handoff before revisiting this.
val fetchTessdata = tasks.register("fetchTessdata") {
    // Locals only (no script/Project references) so the task is
    // configuration-cache compatible.
    val outDir = layout.projectDirectory.dir("src/main/assets/tessdata").asFile
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
// Both flavors bundle the models now (see above).
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(fetchTessdata) }
// Lint (vital) also scans the assets dir, so it must see the same ordering,
// otherwise release builds fail Gradle's implicit-dependency validation.
tasks.matching { it.name.contains("lint", ignoreCase = true) }
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
    // v1.7: ONE OCR engine for both flavors. ML Kit stays for barcode decoding
    // only — it reads a QR better than ZXing, but it cannot read Cyrillic text
    // at all, and Cyrillic is what these bills are printed in.
    implementation(libs.tesseract4android)

    "gmsImplementation"(libs.mlkit.barcode.scanning)
    "gmsImplementation"(libs.kotlinx.coroutines.play.services)
    "gmsImplementation"(libs.zxing.core)

    // foss flavor engines (ZXing decode+encode; Tesseract OCR, models bundled)
    "fossImplementation"(libs.zxing.core)

    testImplementation(libs.junit)
    "ksp"(libs.androidx.room.compiler)
}
