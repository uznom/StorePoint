package com.munzo.storepoint.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BluePrimaryDark,
    secondary = BlueSecondaryDark,
    tertiary = BlueTertiaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = BlueTertiary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
  )

/**
 * Primary theme for StorePoint POS.
 * Full Material 3 Expressive theme (Android 17 / M3E):
 *  - Physically-based expressive MotionScheme (bouncy springs, M3E duration tokens).
 *  - "Reduce animations" accessibility setting honored: all expressive motion snaps.
 *
 * ## Why dynamic color is OFF by default
 *
 * [dynamicColor] remains available, but defaults to `false`. With it enabled, Android 12+
 * (which is every supported device - minSdk is 34) replaces the entire palette with
 * wallpaper-derived colors, including `primary`, `onPrimaryContainer` and the
 * `surfaceContainer*` ramp. That has two consequences that matter for a POS terminal:
 *
 *  1. **Contrast is no longer verifiable.** The hand-tuned palette below is audited to
 *     WCAG AA (see `ThemeContrastTest`). A dynamic scheme derives its roles from an
 *     arbitrary wallpaper hue, so icon-on-surface pairs that pass today can silently
 *     drop below AA on a device whose wallpaper happens to be mid-tone - which is
 *     exactly the "icons blend into their background" symptom.
 *  2. **The terminal stops looking like the POS it is.** A sari-sari register should
 *     look the same on every device in the chain, and staff should not have to learn
 *     that swapping the wallpaper changes button contrast.
 *
 * Set `dynamicColor = true` at the call site if brand personalization is ever wanted.
 */
@Composable
fun StorePointTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val reduceMotion = androidx.compose.runtime.remember { readSystemReduceMotion(context) }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  CompositionLocalProvider(
    LocalReduceMotion provides reduceMotion,
    LocalExpressiveColors provides if (darkTheme) ExpressiveRolesDark else ExpressiveRolesLight
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      shapes = ExpressiveShapes,
      typography = Typography,
      content = content
    )
  }
}

/**
 * Backward-compatible alias for StorePointTheme.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  StorePointTheme(
    darkTheme = darkTheme,
    dynamicColor = dynamicColor,
    content = content
  )
}
