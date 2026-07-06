plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.binaryCompat)
}

kotlin {
    // These models use expect/actual classes, still marked Beta in Kotlin; opt in to silence the
    // warning.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.clientWrapper.models)
                implementation(libs.coroutines.core)
                implementation(libs.kotlinx.serialization)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization)
            }
        }

        val jvmMain by getting { dependencies { implementation(kotlin("stdlib-jdk8")) } }
    }
}
