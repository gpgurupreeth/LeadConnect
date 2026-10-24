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
    val cardRadius = 14.dp
    val buttonRadius = 10.dp
    val pillRadius = 20.dp
}

// Semantic Status Colors (Warm & Executive)
object AppStatusColors {
    val successLight = Color(0xFF15803D)
    val successBgLight = Color(0xFFDCFCE7)

    val warningLight = Color(0xFFC2410C)       // Deep Amber Orange
    val warningBgLight = Color(0xFFFFEDD5)     // Soft Cream Orange

    val errorLight = Color(0xFFDC2626)
    val errorBgLight = Color(0xFFFEE2E2)

    val infoLight = Color(0xFFEA580C)          // Primary Brand Orange
    val infoBgLight = Color(0xFFFFF7ED)

    val suppressedLight = Color(0xFF6B7280)
    val suppressedBgLight = Color(0xFFF3F4F6)
}

// Light Cream & Orange Palette with Crisp Black Text
private val LightCreamOrangeColorScheme = lightColorScheme(
    primary = Color(0xFFEA580C),              // Vibrant Brand Orange
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),     // Warm Peach/Cream Container
    onPrimaryContainer = Color(0xFF9A3412),

    secondary = Color(0xFFC2410C),            // Rust Orange
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7ED),
    onSecondaryContainer = Color(0xFF7C2D12),

    tertiary = Color(0xFFF97316),             // Amber Orange
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF431407),

    background = Color(0xFFFAF7F2),           // Soft Warm Light Cream
    onBackground = Color(0xFF0F0F0F),         // Crisp Solid Black Text
    surface = Color(0xFFFFFFFF),              // Pure Clean Card Surface
    onSurface = Color(0xFF0A0A0A),            // Crisp Black Headings & Text
    surfaceVariant = Color(0xFFF5EFE6),       // Light Cream Tile Background
    onSurfaceVariant = Color(0xFF262626),     // Dark Slate Black Secondary Text

    outline = Color(0xFFE5DDD0),              // Warm Cream Border
    outlineVariant = Color(0xFFECE4D8),

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
    MaterialTheme(
        colorScheme = LightCreamOrangeColorScheme,
        typography = AppTypography,
        content = content
    )
}
