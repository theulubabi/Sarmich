package uz.usar.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand: deep caravan teal with a warm desert-gold accent.
private val Teal = Color(0xFF0E6B66)
private val TealLight = Color(0xFF7FD3CB)
private val Gold = Color(0xFFF2C14E)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7EDE8),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A6A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7B0),
    onSecondaryContainer = Color(0xFF2B2000),
    tertiary = Color(0xFF4C5F7C),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFBFAF7),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE2E6E3),
    onSurfaceVariant = Color(0xFF424947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F4F0),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE9E8E4),
    surfaceContainerHighest = Color(0xFFE3E2DE),
    outline = Color(0xFF727976),
    outlineVariant = Color(0xFFC2C8C5),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFC7EDE8),
    secondary = Gold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFCE7B0),
    tertiary = Color(0xFFB4C7E8),
    background = Color(0xFF151817),
    onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF151817),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainerLowest = Color(0xFF0F1211),
    surfaceContainerLow = Color(0xFF1B1E1D),
    surfaceContainer = Color(0xFF1F2221),
    surfaceContainerHigh = Color(0xFF2A2D2B),
    surfaceContainerHighest = Color(0xFF343836),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFF2B8B5),
)

/** Toolbar colours for private tabs, so private mode is always obvious. */
object PrivateColors {
    val toolbar = Color(0xFF26203A)
    val field = Color(0xFF3A3354)
    val content = Color(0xFFEDE7FA)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
}

@Composable
fun UsarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
