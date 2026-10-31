package dev.pocket.agent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PockoColors {
    // Dark Theme - Deep Obsidian & Sleek Slates
    val DarkBackground = Color(0xFF090A0F)
    val DarkSurface = Color(0xFF12141C)
    val DarkSurfaceElevated = Color(0xFF181B26)
    val DarkSurfaceHighlight = Color(0xFF222636)
    val DarkBorder = Color(0xFF242838)
    val DarkBorderSubtle = Color(0xFF1E212E)

    // Light Theme - Crisp Porcelain & Soft Slates
    val LightBackground = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF1F5F9)
    val LightBorder = Color(0xFFE2E8F0)

    // Accents & Gradients
    val PrimaryBlue = Color(0xFF3B82F6)
    val PrimaryIndigo = Color(0xFF4F46E5)
    val AccentCyan = Color(0xFF06B6D4)
    val EmeraldReady = Color(0xFF10B981)
    val AmberWorking = Color(0xFFF59E0B)
    val RoseAttention = Color(0xFFF43F5E)
    val VioletAccent = Color(0xFF8B5CF6)

    val PrimaryGradient = Brush.linearGradient(
        listOf(PrimaryIndigo, PrimaryBlue)
    )

    val GlassSurfaceGradient = Brush.linearGradient(
        listOf(Color(0xFF181B26).copy(alpha = 0.85f), Color(0xFF12141C).copy(alpha = 0.75f))
    )

    val GlassBorderGradient = Brush.linearGradient(
        listOf(Color(0xFF3B82F6).copy(alpha = 0.35f), Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF242838))
    )

    // Text Colors
    val TextPrimaryDark = Color(0xFFF8FAFC)
    val TextSecondaryDark = Color(0xFF94A3B8)
    val TextTertiaryDark = Color(0xFF64748B)

    val TextPrimaryLight = Color(0xFF0F172A)
    val TextSecondaryLight = Color(0xFF64748B)
}

val PockoCardShape = RoundedCornerShape(18.dp)
val PockoPillShape = RoundedCornerShape(28.dp)
val PockoSmallShape = RoundedCornerShape(12.dp)

@Composable
fun PockoTheme(
    darkTheme: Boolean = AgentStyle.dark,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = PockoColors.DarkBackground,
            surface = PockoColors.DarkSurface,
            surfaceVariant = PockoColors.DarkSurfaceElevated,
            primary = PockoColors.PrimaryBlue,
            secondary = PockoColors.AccentCyan,
            outline = PockoColors.DarkBorder,
            onBackground = PockoColors.TextPrimaryDark,
            onSurface = PockoColors.TextPrimaryDark,
            onSurfaceVariant = PockoColors.TextSecondaryDark
        )
    } else {
        lightColorScheme(
            background = PockoColors.LightBackground,
            surface = PockoColors.LightSurface,
            surfaceVariant = PockoColors.LightSurfaceElevated,
            primary = PockoColors.PrimaryBlue,
            secondary = PockoColors.AccentCyan,
            outline = PockoColors.LightBorder,
            onBackground = PockoColors.TextPrimaryLight,
            onSurface = PockoColors.TextPrimaryLight,
            onSurfaceVariant = PockoColors.TextSecondaryLight
        )
    }

    val family = FontFamily(Font(R.font.inter))
    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
