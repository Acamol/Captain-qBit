@file:Suppress("UnstableApiUsage")

import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

val commitHash: String by lazy {
    providers
        .exec { commandLine("git").args("rev-parse", "--short", "HEAD").workingDir(projectDir) }
        .standardOutput
        .asText
        .get()
        .trim()
}

// In-app "What's New": bundle the per-release notes into the APK as assets so they're available
// offline in-app. Generated during the Gradle build (see the syncChangelogAssets task), so an
// F-Droid source build produces it identically.
//
// Two sources, because F-Droid truncates its "What's New" listing at 500 chars but the in-app
// dialog has no such limit:
//   - fastlane/.../changelogs/<versionCode>.txt — the short (<=500) summary F-Droid shows.
//   - whatsnew/<versionCode>.txt               — the full in-app notes (optional, no limit).
// The task copies the fastlane summary, then overlays the full version when one exists, so the app
// shows the richer text while F-Droid keeps the short summary.
val changelogSource = rootProject.file("fastlane/metadata/android/en-US/changelogs")
val fullChangelogSource = rootProject.file("whatsnew")

plugins {
    alias(libs.plugins.android.application)
    id("dev.yashgarg.qbit.kotlin-android")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
}

// Copies the fastlane changelog notes into a generated assets dir as assets/changelogs/*.txt.
// Declared after the plugins block (Gradle requires that block to come first). See usage below.
abstract class SyncChangelogAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    // Full in-app notes overlaid on top of the fastlane summary; optional (older versions have
    // none).
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fullSource: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val dest = outputDir.get().asFile.resolve("changelogs")
        dest.deleteRecursively()
        dest.mkdirs()
        source
            .get()
            .asFile
            .listFiles { file -> file.extension == "txt" }
            ?.forEach { file ->
                file.copyTo(dest.resolve(file.name), overwrite = true)
            }
        // Overlay the full in-app notes where present, so the app shows the richer text while
        // F-Droid keeps the short fastlane summary.
        val full = fullSource.orNull?.asFile
        if (full != null && full.isDirectory) {
            full
                .listFiles { file -> file.extension == "txt" }
                ?.forEach { file -> file.copyTo(dest.resolve(file.name), overwrite = true) }
        }
    }
}

android {
    namespace = "dev.yashgarg.qbit"
    compileSdk = 37

    defaultConfig {
        // Distinct from upstream (namespace stays dev.yashgarg.qbit) so this fork can be
        // installed alongside the original app.
        applicationId = "dev.acamol.qbit"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release signing via a gitignored keystore.properties (never committed).
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
        signingConfigs {
            register("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        buildTypes.getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    androidComponents {
        beforeVariants {
            if (it.name.contains("benchmark", true)) {
                it.enable = false
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = true
        disable.add("PluralsCandidate")
        // Dependency freshness is managed by Dependabot, so don't fail the build on these
        // "a newer version is available" checks (they'd also recur after every Dependabot bump).
        disable.add("GradleDependency")
        disable.add("NewerVersionAvailable")
        disable.add("AndroidGradlePluginVersion")
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/kotlin/**"
            excludes += "**/*.txt"
            excludes += "**/*.xml"
            excludes += "**/*.properties"
        }
    }
}

// Register the changelog sync as a generated assets dir on every variant.
// addGeneratedSourceDirectory
// carries the task dependency automatically, so the notes are present for any build — including
// F-Droid's `assembleRelease` — without relying on task ordering.
val syncChangelogAssets =
    tasks.register<SyncChangelogAssets>("syncChangelogAssets") {
        source.fileValue(changelogSource)
        if (fullChangelogSource.isDirectory) fullSource.fileValue(fullChangelogSource)
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncChangelogAssets,
            SyncChangelogAssets::outputDir,
        )
    }
}

base.archivesName.set("dev.acamol.qbit-0.7.0-$commitHash")

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.recyclerview.selection)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.bundles.compose)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)
    implementation(libs.compose.hilt.navigation)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.androidx.fragment.compose)

    implementation(libs.google.material)
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization)

    implementation(projects.uiCompose)
    implementation(projects.common)
    implementation(projects.clientWrapper.client)
    implementation(projects.clientWrapper.models)

    debugImplementation(libs.tools.leakcanary)
    implementation(libs.tools.kotlin.result)
    implementation(libs.tools.cascade)
    implementation(libs.tools.lottie)
    debugImplementation(libs.tools.whatthestack)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktor.client.mock)
}
