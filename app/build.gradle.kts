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
        versionCode = 7
        versionName = "1.5.2"
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
            isMinifyEnabled = false
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
    implementation(libs.androidx.compose.ui.tooling.preview)
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
