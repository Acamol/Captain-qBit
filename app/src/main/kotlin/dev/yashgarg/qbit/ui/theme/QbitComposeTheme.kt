package dev.yashgarg.qbit.ui.theme

import android.content.Context
import androidx.annotation.AttrRes
import androidx.appcompat.R as AppcompatR
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import dev.yashgarg.qbit.ui.compose.theme.SpaceGrotesk

/**
 * Compose Material 3 theme that mirrors the app's XML `Theme.Qbit` (including the Material You
 * dynamic-color overlay applied at runtime) by reading the resolved theme's color attributes, and
 * uses Space Grotesk as the default font family. Drop-in replacement for the deprecated accompanist
 * `Mdc3Theme`.
 */
@Composable
fun QbitComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = remember(context, darkTheme) { context.readColorScheme(darkTheme) }
    val typography = remember { spaceGroteskTypography() }
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

private fun Context.readColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()

    // Resolve a theme color attribute, falling back to the baseline M3 scheme when the theme
    // (or the current API level's dynamic overlay) doesn't define it.
    fun color(@AttrRes attr: Int, fallback: Color): Color =
        Color(MaterialColors.getColor(this, attr, fallback.toArgb()))

    return base.copy(
        primary = color(AppcompatR.attr.colorPrimary, base.primary),
        onPrimary = color(MaterialR.attr.colorOnPrimary, base.onPrimary),
        primaryContainer = color(MaterialR.attr.colorPrimaryContainer, base.primaryContainer),
        onPrimaryContainer = color(MaterialR.attr.colorOnPrimaryContainer, base.onPrimaryContainer),
        inversePrimary = color(MaterialR.attr.colorPrimaryInverse, base.inversePrimary),
        secondary = color(MaterialR.attr.colorSecondary, base.secondary),
        onSecondary = color(MaterialR.attr.colorOnSecondary, base.onSecondary),
        secondaryContainer = color(MaterialR.attr.colorSecondaryContainer, base.secondaryContainer),
        onSecondaryContainer =
            color(MaterialR.attr.colorOnSecondaryContainer, base.onSecondaryContainer),
        tertiary = color(MaterialR.attr.colorTertiary, base.tertiary),
        onTertiary = color(MaterialR.attr.colorOnTertiary, base.onTertiary),
        tertiaryContainer = color(MaterialR.attr.colorTertiaryContainer, base.tertiaryContainer),
        onTertiaryContainer =
            color(MaterialR.attr.colorOnTertiaryContainer, base.onTertiaryContainer),
        background = color(android.R.attr.colorBackground, base.background),
        onBackground = color(MaterialR.attr.colorOnBackground, base.onBackground),
        surface = color(MaterialR.attr.colorSurface, base.surface),
        onSurface = color(MaterialR.attr.colorOnSurface, base.onSurface),
        surfaceVariant = color(MaterialR.attr.colorSurfaceVariant, base.surfaceVariant),
        onSurfaceVariant = color(MaterialR.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
        inverseSurface = color(MaterialR.attr.colorSurfaceInverse, base.inverseSurface),
        inverseOnSurface = color(MaterialR.attr.colorOnSurfaceInverse, base.inverseOnSurface),
        error = color(AppcompatR.attr.colorError, base.error),
        onError = color(MaterialR.attr.colorOnError, base.onError),
        errorContainer = color(MaterialR.attr.colorErrorContainer, base.errorContainer),
        onErrorContainer = color(MaterialR.attr.colorOnErrorContainer, base.onErrorContainer),
        outline = color(MaterialR.attr.colorOutline, base.outline),
        outlineVariant = color(MaterialR.attr.colorOutlineVariant, base.outlineVariant),
    )
}

// The XML theme sets Space Grotesk as the global font family; apply it to every role so Compose
// content matches the rest of the app (what Mdc3Theme's setDefaultFontFamily did).
private fun spaceGroteskTypography(): Typography {
    val base = Typography()
    fun TextStyle.sg(): TextStyle = copy(fontFamily = SpaceGrotesk)
    return base.copy(
        displayLarge = base.displayLarge.sg(),
        displayMedium = base.displayMedium.sg(),
        displaySmall = base.displaySmall.sg(),
        headlineLarge = base.headlineLarge.sg(),
        headlineMedium = base.headlineMedium.sg(),
        headlineSmall = base.headlineSmall.sg(),
        titleLarge = base.titleLarge.sg(),
        titleMedium = base.titleMedium.sg(),
        titleSmall = base.titleSmall.sg(),
        bodyLarge = base.bodyLarge.sg(),
        bodyMedium = base.bodyMedium.sg(),
        bodySmall = base.bodySmall.sg(),
        labelLarge = base.labelLarge.sg(),
        labelMedium = base.labelMedium.sg(),
        labelSmall = base.labelSmall.sg(),
    )
}
