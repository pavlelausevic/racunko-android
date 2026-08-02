plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// `mkaccount` helper for contributors (see CONTRIBUTING.md):
//   ./gradlew :parser-core:run --args="mkaccount 190 99870"
application {
    mainClass.set("com.racunko.app.parser.tools.MkaccountKt")
}

/**
 * :parser-core — the brain (Change 1). Pure Kotlin/JVM, ZERO Android
 * dependencies, so every parsing/pairing/checksum/QR-payload rule is testable
 * and contributable without an emulator. If you ever need something from the
 * device here, it belongs in :platform-api instead (see ARCHITECTURE.md).
 */
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// Fixtures live under src/test/fixtures (see TESTING.md); expose them on the
// test classpath so the parameterized fixture test can auto-discover them.
sourceSets {
    named("test") {
        resources.srcDir("src/test/fixtures")
    }
}
