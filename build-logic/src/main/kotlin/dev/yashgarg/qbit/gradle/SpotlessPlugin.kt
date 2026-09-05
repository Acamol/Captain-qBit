package dev.yashgarg.qbit.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import com.diffplug.spotless.LineEnding
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

@Suppress("Unused")
class SpotlessPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(SpotlessPlugin::class)
        project.extensions.getByType<SpotlessExtension>().run {
            /** Workaround for https://github.com/diffplug/spotless/issues/1644 */
            lineEndings = LineEnding.UNIX

            kotlin {
                ktfmt().kotlinlangStyle()
                target("**/*.kt")
                targetExclude("**/build/")
                trimTrailingWhitespace()
                endWithNewline()
            }

            kotlinGradle {
                ktfmt().kotlinlangStyle()
                target("**/*.gradle.kts")
                targetExclude("**/build/")
            }

            format("xml") {
                target("**/*.xml")
                // Translation resources are written by Weblate, which never runs this formatter,
                // so its output (notably locale stubs with no trailing newline) would fail the
                // check on every rolling translation PR. The default-locale values/strings.xml is
                // hand-authored and stays covered - only the hyphenated locale variants are not.
                targetExclude("**/build/", ".idea/", "**/res/values-*/strings.xml")
                trimTrailingWhitespace()
                leadingTabsToSpaces()
                endWithNewline()
            }
        }
    }
}
