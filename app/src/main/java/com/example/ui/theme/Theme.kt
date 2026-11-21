package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SaffronLightColorScheme = lightColorScheme(
    primary = SaffronPrimary,
    onPrimary = PureWhite,
    primaryContainer = SaffronLight,
    onPrimaryContainer = SaffronDark,
    secondary = SaffronDark,
    onSecondary = PureWhite,
    secondaryContainer = WarmSurfaceVariant,
    onSecondaryContainer = MainText,
    tertiary = SuccessGreen,
    onTertiary = PureWhite,
    tertiaryContainer = SuccessLight,
    onTertiaryContainer = SuccessGreen,
    background = PureWhite,
    onBackground = MainText,
    surface = WarmSurface,
    onSurface = MainText,
    surfaceVariant = SaffronLight,
    onSurfaceVariant = SecondaryText,
    outline = OutlineWarm,
    outlineVariant = WarmSurfaceVariant,
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = ErrorLight,
    onErrorContainer = ErrorRed,
)

private val SaffronDarkColorScheme = darkColorScheme(
    primary = SaffronBright,
    onPrimary = DarkBackground,
    primaryContainer = SaffronDark,
    onPrimaryContainer = SaffronLight,
    secondary = SaffronPrimary,
    onSecondary = PureWhite,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = SuccessGreen,
    onTertiary = PureWhite,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = SaffronDark,
    error = ErrorRed,
    onError = PureWhite,
)

val SaffronShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StudentsAttendanceTheme(
    darkTheme: Boolean = false,
    useBengaliFont: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SaffronDarkColorScheme else SaffronLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildAppTypography(useBengaliFont),
        shapes = SaffronShapes,
        content = content
    )
}
