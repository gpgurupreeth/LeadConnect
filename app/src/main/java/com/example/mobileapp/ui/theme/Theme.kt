package com.example.mobileapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Design System Spacing Tokens (8dp Grid)
object AppSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val minTouchTarget = 48.dp
    val cardRadius = 14.dp
    val buttonRadius = 10.dp
    val pillRadius = 20.dp
}

// Semantic Status Colors
object AppStatusColors {
    val successLight = Color(0xFF15803D)
    val successBgLight = Color(0xFFDCFCE7)
    val successDark = Color(0xFF4ADE80)
    val successBgDark = Color(0xFF14532D)

    val warningLight = Color(0xFFB45309)
    val warningBgLight = Color(0xFFFEF3C7)
    val warningDark = Color(0xFFFBBF24)
    val warningBgDark = Color(0xFF78350F)

    val errorLight = Color(0xFFB91C1C)
    val errorBgLight = Color(0xFFFEE2E2)
    val errorDark = Color(0xFFF87171)
    val errorBgDark = Color(0xFF7F1D1D)

    val infoLight = Color(0xFF1D4ED8)
    val infoBgLight = Color(0xFFDBEAFE)
    val infoDark = Color(0xFF60A5FA)
    val infoBgDark = Color(0xFF1E3A8A)

    val suppressedLight = Color(0xFF6D28D9)
    val suppressedBgLight = Color(0xFFF3E8FF)
    val suppressedDark = Color(0xFFA78BFA)
    val suppressedBgDark = Color(0xFF4C1D95)
}

// Light Palette
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E40AF),         // Royal Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),

    secondary = Color(0xFF0F766E),       // Teal
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),

    tertiary = Color(0xFF4338CA),        // Indigo
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),

    background = Color(0xFFF8FAFC),      // Crisp Off-White
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),  // Light Slate
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

// Dark Palette
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),

    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),

    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFE0E7FF),

    background = Color(0xFF0B132B),      // Deep Midnight
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1C2541),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF24344D),
    onSurfaceVariant = Color(0xFF94A3B8),

    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),

    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2)
)

@Composable
fun MobileAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
