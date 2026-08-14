package com.linkvault.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = IndigoLight,
    onPrimary = OnIndigoLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = OnIndigoContainerLight,
    secondary = SlateLight,
    onSecondary = OnSlateLight,
    tertiary = BronzeLight,
    onTertiary = OnBronzeLight,
    tertiaryContainer = BronzeContainerLight,
    onTertiaryContainer = OnBronzeContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    onPrimary = OnIndigoDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = OnIndigoContainerDark,
    secondary = SlateDark,
    onSecondary = OnSlateDark,
    tertiary = BronzeDark,
    onTertiary = OnBronzeDark,
    tertiaryContainer = BronzeContainerDark,
    onTertiaryContainer = OnBronzeContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark
)

@Composable
fun LinkVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default as of Phase 3: LinkVault now has a deliberately chosen
    // identity (see Color.kt), and Material You's wallpaper-derived dynamic
    // color would just override it on most phones (Android 12+) if this
    // defaulted to true — fine as an opt-in for someone who wants their
    // launcher to blend in with system theming, but not the default look.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
