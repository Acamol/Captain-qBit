@file:Suppress("UnstableApiUsage", "DSL_SCOPE_VIOLATION")

plugins {
    id("dev.yashgarg.qbit.kotlin-android")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.yashgarg.qbit.ui.compose"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint { baseline = file("lint-baseline.xml") }
}

dependencies {
    api(libs.bundles.compose)
    implementation(libs.compose.material.icons)
    implementation(projects.common)
    implementation(projects.bonsaiCore)
}
