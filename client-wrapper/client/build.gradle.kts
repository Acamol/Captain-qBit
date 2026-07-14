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
        commonMain.dependencies {
            api(projects.clientWrapper.models)
            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.serialization)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization)
        }

        jvmMain.dependencies { implementation(kotlin("stdlib-jdk8")) }
    }
}
