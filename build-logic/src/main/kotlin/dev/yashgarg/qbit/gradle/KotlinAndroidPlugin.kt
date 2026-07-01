package dev.yashgarg.qbit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

@Suppress("Unused")
class KotlinAndroidPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(KotlinCommonPlugin::class)
    }
}
