package ru.ama.whereme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.ama.whereme.domain.entity.ThemeMode

internal val LightColors =
    lightColorScheme(
        primaryFixed = Color(0xFFB5EEDD),
        primaryFixedDim = Color(0xFF80D5BC),
        onPrimaryFixed = Color(0xFF00382B),
        onPrimaryFixedVariant = Color(0xFF075044),
        secondaryFixed = Color(0xFFD0E9DC),
        secondaryFixedDim = Color(0xFFB3CFC0),
        onSecondaryFixed = Color(0xFF1F392D),
        onSecondaryFixedVariant = Color(0xFF354E42),
        tertiaryFixed = Color(0xFFD0E7FC),
        tertiaryFixedDim = Color(0xFFA7CBE8),
        onTertiaryFixed = Color(0xFF103449),
        onTertiaryFixedVariant = Color(0xFF2E4A60),
        primary = Color(0xFF00695C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB5EEDD),
        onPrimaryContainer = Color(0xFF00382D),
        secondary = Color(0xFF466458),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD0E9DC),
        onSecondaryContainer = Color(0xFF203E32),
        tertiary = Color(0xFF3E627C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0E7FC),
        onTertiaryContainer = Color(0xFF183A52),
        background = Color(0xFFF6FAF8),
        onBackground = Color(0xFF18251F),
        surface = Color(0xFFF6FAF8),
        onSurface = Color(0xFF18251F),
        surfaceVariant = Color(0xFFDFE8E2),
        onSurfaceVariant = Color(0xFF45534B),
        surfaceDim = Color(0xFFD5DFD8),
        surfaceBright = Color(0xFFF6FAF8),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F5F1),
        surfaceContainer = Color(0xFFEAF0EB),
        surfaceContainerHigh = Color(0xFFE4EBE5),
        surfaceContainerHighest = Color(0xFFDEE5DF),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF65776C),
        outlineVariant = Color(0xFFBBC9C0),
        inverseSurface = Color(0xFF28362E),
        inverseOnSurface = Color(0xFFE8F2EB),
        inversePrimary = Color(0xFF80D5BC),
        surfaceTint = Color(0xFF00695C),
        scrim = Color.Black,
    )
internal val DarkColors =
    darkColorScheme(
        primaryFixed = Color(0xFFB5EEDD),
        primaryFixedDim = Color(0xFF80D5BC),
        onPrimaryFixed = Color(0xFF00382B),
        onPrimaryFixedVariant = Color(0xFF075044),
        secondaryFixed = Color(0xFFD0E9DC),
        secondaryFixedDim = Color(0xFFB3CFC0),
        onSecondaryFixed = Color(0xFF1F392D),
        onSecondaryFixedVariant = Color(0xFF354E42),
        tertiaryFixed = Color(0xFFD0E7FC),
        tertiaryFixedDim = Color(0xFFA7CBE8),
        onTertiaryFixed = Color(0xFF103449),
        onTertiaryFixedVariant = Color(0xFF2E4A60),
        primary = Color(0xFF80D5BC),
        onPrimary = Color(0xFF00382B),
        primaryContainer = Color(0xFF075044),
        onPrimaryContainer = Color(0xFFB5EEDD),
        secondary = Color(0xFFB3CFC0),
        onSecondary = Color(0xFF1F392D),
        secondaryContainer = Color(0xFF354E42),
        onSecondaryContainer = Color(0xFFD0E9DC),
        tertiary = Color(0xFFA7CBE8),
        onTertiary = Color(0xFF103449),
        tertiaryContainer = Color(0xFF2E4A60),
        onTertiaryContainer = Color(0xFFD0E7FC),
        background = Color(0xFF101914),
        onBackground = Color(0xFFE3EEE6),
        surface = Color(0xFF101914),
        onSurface = Color(0xFFE3EEE6),
        surfaceVariant = Color(0xFF3C4B41),
        onSurfaceVariant = Color(0xFFBBCBBF),
        surfaceDim = Color(0xFF101914),
        surfaceBright = Color(0xFF354139),
        surfaceContainerLowest = Color(0xFF0B130E),
        surfaceContainerLow = Color(0xFF17221A),
        surfaceContainer = Color(0xFF1B261F),
        surfaceContainerHigh = Color(0xFF253028),
        surfaceContainerHighest = Color(0xFF303B33),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8B9D90),
        outlineVariant = Color(0xFF3F5145),
        inverseSurface = Color(0xFFE3EEE6),
        inverseOnSurface = Color(0xFF28362E),
        inversePrimary = Color(0xFF00695C),
        surfaceTint = Color(0xFF80D5BC),
        scrim = Color.Black,
    )

@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

@Composable
fun WhereMeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
