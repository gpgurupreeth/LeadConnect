package com.example.mobileapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val cardRadius = 24.dp       // Luxurious Dribbble-style rounded corners
    val buttonRadius = 14.dp
    val pillRadius = 28.dp       // Full pill segmented controls
}

// Semantic Status Colors (Clean & Modern)
object AppStatusColors {
    val successLight = Color(0xFF16A34A)       // Crisp Emerald
    val successBgLight = Color(0xFFDCFCE7)

    val warningLight = Color(0xFFD97706)       // Warm Amber
    val warningBgLight = Color(0xFFFEF3C7)

    val errorLight = Color(0xFFDC2626)         // Crisp Coral Red
    val errorBgLight = Color(0xFFFEE2E2)

    val infoLight = Color(0xFF0284C7)          // Electric Logo Cyan/Blue
    val infoBgLight = Color(0xFFE0F2FE)

    val suppressedLight = Color(0xFF71717A)    // Neutral Slate
    val suppressedBgLight = Color(0xFFF4F4F5)

    val activeCardDark = Color(0xFF0284C7)     // Electric Blue Brand Card Accent
    val brandBlue = Color(0xFF0284C7)          // LeadConnect Electric Blue
    val brandBlueLight = Color(0xFFE0F2FE)
    val brandCyan = Color(0xFF38BDF8)          // Accent Cyan
}

// Minimalist Blue & White Light Palette (Pure white cards, crisp blue accents, high-contrast black texts)
private val DribbbleMinimalColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),              // Electric Blue Brand
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),     // Soft Blue Container
    onPrimaryContainer = Color(0xFF0369A1),

    secondary = Color(0xFF0284C7),            // Electric Blue Brand Accent
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),

    tertiary = Color(0xFF38BDF8),             // Cyan Highlight
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),

    background = Color(0xFFF8FAFC),           // Clean Crisp White Canvas
    onBackground = Color(0xFF09090B),         // High-Contrast Pure Charcoal/Black
    surface = Color(0xFFFFFFFF),              // Crisp White Cards
    onSurface = Color(0xFF09090B),            // Crisp Black Headings & Text
    surfaceVariant = Color(0xFFF0F9FF),       // Soft Sky-White Tile
    onSurfaceVariant = Color(0xFF64748B),     // Clean Slate Secondary

    outline = Color(0xFFE2E8F0),              // Clean Ultra-Subtle Border
    outlineVariant = Color(0xFFF1F5F9),

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

/**
 * Distinct, professional typography hierarchy:
 * - Headers: Bold, heavy Black/ExtraBold, tight letter spacing, high contrast black
 * - Body: Normal weight, charcoal for comfortable readability, relaxed line height
 */
val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.5).sp,
        color = Color(0xFF0A0A0A)
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp,
        color = Color(0xFF0A0A0A)
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
        color = Color(0xFF0A0A0A)
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = Color(0xFF0A0A0A)
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color(0xFF374151)
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        color = Color(0xFF374151)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        color = Color(0xFF4B5563)
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
        color = Color(0xFF6B7280)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = Color(0xFF0A0A0A)
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = Color(0xFF4B5563)
    )
)

@Composable
fun MobileAppTheme(content: @Composable () -> Unit) {
    // Enforce consistent density and font scale so UI never stretches, breaks or truncates across different phone models
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val normalizedDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = 1.0f // Fixed fontScale guarantees identical layout across all devices regardless of system font scale
    )

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides normalizedDensity
    ) {
        MaterialTheme(
            colorScheme = DribbbleMinimalColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
