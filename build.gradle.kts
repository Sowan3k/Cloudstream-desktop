import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

group = "com.lagradost.cloudstream"
version = "1.0.3"

kotlin {
    jvm("jvm")

    sourceSets {
        val jvmMain by getting {
            kotlin.srcDirs(
                "src/jvmMain/kotlin",
                "../nehal-CloudStream/AllWish/src/main/kotlin",
                "../nehal-CloudStream/Aniwatch/src/main/kotlin",
                "../nehal-CloudStream/BdixCircleftp/src/main/kotlin",
                "../nehal-CloudStream/BdixICCFtp/src/main/kotlin",
                "../nehal-CloudStream/CTGMovies/src/main/kotlin",
                "../nehal-CloudStream/CineplexBD/src/main/kotlin",
                "../nehal-CloudStream/DhakaFlix/src/main/kotlin",
                "../nehal-CloudStream/DhakaFlixBDIX/src/main/kotlin",
                "../nehal-CloudStream/DiscoveryFTP/src/main/kotlin",
                "../nehal-CloudStream/FTPBD/src/main/kotlin",
                "../nehal-CloudStream/FmFtp/src/main/kotlin",
                "../nehal-CloudStream/JellyfinBD/src/main/kotlin",
                "../nehal-CloudStream/ShowTimeBD/src/main/kotlin",
                "../nehal-CloudStream/VegaMovies/src/main/kotlin",
                "../nehal-CloudStream/MovieBoxProviderIN/src/main/kotlin"
            )
            dependencies {
                // CloudStream Core KMP Library
                implementation("com.lagradost.api:library")

                // Compose Desktop UI & Material 3
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                // Coroutines & Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

                // Network & Parsing
                implementation("org.jsoup:jsoup:1.17.2")
                implementation("org.mozilla:rhino:1.7.14")
                implementation("com.github.Blatzar:NiceHttp:0.4.11")
                implementation("org.json:json:20231013")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

                // Desktop Video Player Engine (VLCj for format detection only)
                implementation("uk.co.caprica:vlcj:4.8.2")

                // Coil Image Loader for Desktop
                implementation("io.coil-kt.coil3:coil-compose:3.0.0-rc01")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.0-rc01")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0-rc01")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CloudStreamDesktop"
            packageVersion = "1.0.3"
            // vlcj/JNA callbacks rely on JDK modules beyond Compose Desktop's
            // minimal runtime image. Keep the complete runtime in the MSI so
            // the packaged app has the same capabilities as `gradlew run`.
            includeAllModules = true
            description = "CloudStream Windows Desktop Application"
            copyright = "© 2026 Openstream / CloudStream Team"

            windows {
                menuGroup = "CloudStream"
                upgradeUuid = "a2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e"
                dirChooser = true
                perUserInstall = false
            }
        }

        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}
