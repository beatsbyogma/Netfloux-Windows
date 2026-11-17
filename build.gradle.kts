import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI

// Netfloux pour Windows : la même application que sur la TV, avec Compose pour Windows
// et le moteur de VLC pour la lecture.
plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.compose") version "1.10.0"
}

group = "com.cinetv"
version = "1.0.0"

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-Xsuppress-warning=NOTHING_TO_INLINE",
            "-nowarn"
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Lecture : le moteur de VLC
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Bibliothèque, partages réseau, TMDB
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.json:json:20240303")
    implementation("org.slf4j:slf4j-nop:2.0.13")
}

// ---------------------------------------------------------------- journal de compilation
// Les erreurs de compilation sont aussi écrites dans build/compile-log.txt :
// on peut les relire sans passer par l'écran d'Android Studio.
val compileLog = layout.buildDirectory.file("compile-log.txt")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val logFile = compileLog.get().asFile
    doFirst { logFile.parentFile.mkdirs(); logFile.writeText("") }
    logging.addStandardOutputListener { logFile.appendText(it.toString()) }
    logging.addStandardErrorListener { logFile.appendText(it.toString()) }
}

// ------------------------------------------------------------- VLC embarqué
// Le moteur de VLC (3.0.21, 64 bits) est téléchargé une fois et rangé avec l'application :
// Netfloux n'a pas besoin que VLC soit installé sur le PC.
val vlcVersion = "3.0.21"
val vlcZip = layout.buildDirectory.file("vlc-download/vlc-$vlcVersion-win64.zip")
val appResources = layout.buildDirectory.dir("app-resources")

val downloadVlc by tasks.registering {
    val target = vlcZip
    outputs.file(target)
    onlyIf { !target.get().asFile.exists() }
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        val url = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
        runCatching {
            val partial = File(file.path + ".part")
            URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial.renameTo(file)
        }.onFailure { logger.warn("VLC n'a pas pu être téléchargé ($url) : ${it.message}") }
    }
}

val unpackVlc by tasks.registering(Copy::class) {
    dependsOn(downloadVlc)
    onlyIf { vlcZip.get().asFile.exists() }
    from({ if (vlcZip.get().asFile.exists()) zipTree(vlcZip) else files() }) {
        include("vlc-$vlcVersion/libvlc.dll", "vlc-$vlcVersion/libvlccore.dll", "vlc-$vlcVersion/plugins/**")
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(appResources.map { it.dir("windows/vlc") })
}

// Un JDK complet (Temurin 21) : celui d'Android Studio n'a pas jpackage, nécessaire pour l'exe
val packagingJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.ADOPTIUM)
}

compose.desktop {
    application {
        mainClass = "com.cinetv.MainKt"
        javaHome = packagingJdk.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("-Xmx2g", "-Dfile.encoding=UTF-8", "-Dsun.java2d.uiScale.enabled=true")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Netfloux"
            packageVersion = "1.0.0"
            description = "Netfloux — médiathèque personnelle"
            vendor = "Ogma"
            appResourcesRootDir.set(appResources)
            modules(
                "java.sql", "java.naming", "jdk.unsupported", "java.management", "jdk.crypto.ec",
                "java.net.http", "java.security.jgss", "jdk.crypto.cryptoki", "java.prefs", "jdk.charsets", "jdk.localedata", "jdk.zipfs", "jdk.accessibility"
            )
            windows {
                iconFile.set(project.file("src/main/resources/netfloux.ico"))
                menuGroup = "Netfloux"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6c1c9c62-2f4f-4f0e-9c3a-8f2b7f5f6a11"
            }
        }
    }
}

tasks.matching { it.name in setOf("prepareAppResources", "run", "createDistributable", "packageMsi", "packageExe") }
    .configureEach { dependsOn(unpackVlc) }
