plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.binaryCompat)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.core)
            implementation(libs.kotlinx.serialization)
        }
    }
}
