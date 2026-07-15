package dev.yashgarg.qbit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.ui.compose.theme.SpaceGrotesk

/**
 * Compose Material 3 Expressive theme for the app. Uses [MaterialExpressiveTheme] with the
 * expressive [MotionScheme] so every component picks up the Material 3 Expressive motion/shape
 * language, and Space Grotesk as the default font family.
 *
 * Colors are Compose-native: when [dynamicColors] is on (and the device supports it, API 31+) the
 * Material You wallpaper palette is used directly; otherwise the app's brand [appColorScheme] built
 * from the `theme_*` color resources. This replaces the old runtime bridge that mirrored the XML
 * theme's attributes via `MaterialColors`, so a dynamic-color toggle no longer needs an activity
 * recreate — the theme just recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QbitComposeTheme(dynamicColors: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        if (dynamicColors && supportsDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            appColorScheme(darkTheme)
        }
    val typography = remember { spaceGroteskTypography() }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
    ) {
        // The theme alone doesn't set a default content color (that comes from Surface), so Text
        // without an explicit color would fall back to black. Provide onSurface as the default.
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onSurface,
            content = content,
        )
    }
}

/**
 * The app's brand color scheme, read from the `theme_*` color resources (which auto-resolve
 * light/dark via resource qualifiers). Roles without a brand value (tertiary, outlineVariant, …)
 * keep the Material 3 baseline, matching the theme's prior behavior.
 */
@Composable
private fun appColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colorResource(R.color.theme_primary),
        onPrimary = colorResource(R.color.theme_on_primary),
        primaryContainer = colorResource(R.color.theme_primary_container),
        onPrimaryContainer = colorResource(R.color.theme_on_primary_container),
        inversePrimary = colorResource(R.color.theme_inverse_primary),
        secondary = colorResource(R.color.theme_secondary),
        onSecondary = colorResource(R.color.theme_on_secondary),
        secondaryContainer = colorResource(R.color.theme_secondary_container),
        onSecondaryContainer = colorResource(R.color.theme_on_secondary_container),
        background = colorResource(R.color.theme_background),
        onBackground = colorResource(R.color.theme_on_background),
        surface = colorResource(R.color.theme_surface),
        onSurface = colorResource(R.color.theme_on_surface),
        surfaceVariant = colorResource(R.color.theme_surface_variant),
        onSurfaceVariant = colorResource(R.color.theme_on_surface_variant),
        inverseSurface = colorResource(R.color.theme_inverse_surface),
        inverseOnSurface = colorResource(R.color.theme_inverse_on_surface),
        error = colorResource(R.color.theme_error),
        onError = colorResource(R.color.theme_on_error),
        errorContainer = colorResource(R.color.theme_error_container),
        onErrorContainer = colorResource(R.color.theme_on_error_container),
        outline = colorResource(R.color.theme_outline),
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
