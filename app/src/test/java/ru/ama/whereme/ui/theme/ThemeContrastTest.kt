package ru.ama.whereme.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun lightTextAndInteractiveBoundariesAreReadable() = checkScheme(LightColors)

    @Test fun darkTextAndInteractiveBoundariesAreReadable() = checkScheme(DarkColors)

    private fun checkScheme(colors: ColorScheme) =
        with(colors) {
            val textPairs =
                mapOf(
                    "primary" to (onPrimary to primary),
                    "primary container" to (onPrimaryContainer to primaryContainer),
                    "secondary" to (onSecondary to secondary),
                    "secondary container" to (onSecondaryContainer to secondaryContainer),
                    "tertiary" to (onTertiary to tertiary),
                    "tertiary container" to (onTertiaryContainer to tertiaryContainer),
                    "error" to (onError to error),
                    "error container" to (onErrorContainer to errorContainer),
                    "background" to (onBackground to background),
                    "surface variant" to (onSurfaceVariant to surfaceVariant),
                    "inverse" to (inverseOnSurface to inverseSurface),
                    "inverse primary" to (inversePrimary to inverseSurface),
                )
            listOf(
                    listOf(primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant),
                    listOf(
                        secondaryFixed,
                        secondaryFixedDim,
                        onSecondaryFixed,
                        onSecondaryFixedVariant,
                    ),
                    listOf(
                        tertiaryFixed,
                        tertiaryFixedDim,
                        onTertiaryFixed,
                        onTertiaryFixedVariant,
                    ),
                )
                .forEach { roles ->
                    roles.take(2).forEach { background ->
                        roles.drop(2).forEach { foreground ->
                            assertContrast("fixed role", foreground, background, 4.5)
                        }
                    }
                }
            textPairs.forEach { (name, pair) -> assertContrast(name, pair.first, pair.second, 4.5) }
            listOf(
                    surface,
                    surfaceContainerLowest,
                    surfaceContainerLow,
                    surfaceContainer,
                    surfaceContainerHigh,
                    surfaceContainerHighest,
                )
                .forEach { background ->
                    assertContrast("body text", onSurface, background, 4.5)
                    assertContrast("supporting text", onSurfaceVariant, background, 4.5)
                    assertContrast("text button", primary, background, 4.5)
                    assertContrast("error message", error, background, 4.5)
                    assertContrast("input outline", outline, background, 3.0)
                }
        }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val a = luminance(foreground)
        val b = luminance(background)
        val contrast = (max(a, b) + .05) / (min(a, b) + .05)
        assertTrue("$name contrast is $contrast; expected at least $minimum", contrast >= minimum)
    }

    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double =
            if (value <= .04045f) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
}
