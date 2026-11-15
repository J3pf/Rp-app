package dev.j3pf.rp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Escala de espaciado coherente y armoniosa para todo RP. */
object RpSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp

    val screenPadding: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    val itemSpacing: Dp = 10.dp
}

/** Escala de formas redondeadas elegantes y modernas. */
object RpShapes {
    val extraSmall = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(999.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val bubbleUser = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    val bubbleAgent = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
}

/** Escala de formas Material 3 unificada con RpShapes. */
val RpMaterialShapes: Shapes =
    Shapes(
        extraSmall = RpShapes.extraSmall,
        small = RpShapes.small,
        medium = RpShapes.medium,
        large = RpShapes.large,
        extraLarge = RpShapes.extraLarge,
    )

private val BaseTypography = Typography()

/** Tipografía del sistema con tracking refinado y cifras tabulares en métricas. */
val RpTypography: Typography =
    BaseTypography.copy(
        headlineMedium =
            BaseTypography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
        headlineSmall =
            BaseTypography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            ),
        titleMedium =
            BaseTypography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.1).sp,
            ),
        titleSmall =
            BaseTypography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        labelLarge =
            BaseTypography.labelLarge.copy(
                fontFeatureSettings = "tnum",
            ),
        labelMedium =
            BaseTypography.labelMedium.copy(
                fontFeatureSettings = "tnum",
            ),
        labelSmall =
            BaseTypography.labelSmall.copy(
                fontFeatureSettings = "tnum",
                letterSpacing = 0.2.sp,
            ),
    )

/** Colores semánticos adaptados al contraste del fondo (claro u oscuro). */
data class RpSemanticColors(
    val danger: Color,
    val warning: Color,
    val success: Color,
    val working: Color,
    val onWarning: Color,
)

private val DarkSemanticColors =
    RpSemanticColors(
        danger = Color(0xFFFF6E6E),
        warning = Color(0xFFFFC14D),
        success = Color(0xFF4ADE80),
        working = Color(0xFF38BDF8),
        onWarning = Color(0xFF1F1600),
    )

private val LightSemanticColors =
    RpSemanticColors(
        danger = RpColors.danger,
        warning = RpColors.warning,
        success = RpColors.success,
        working = Color(0xFF0284C7),
        onWarning = Color.White,
    )

val ColorScheme.rpSemantic: RpSemanticColors
    get() = if (background.luminance() < 0.5f) DarkSemanticColors else LightSemanticColors

/** Superficies y separadores derivados del tema para jerarquía visual sin ruido. */
data class RpSurfaceTokens(
    val elevatedDeck: Color,
    val insetBlock: Color,
    val hairline: Color,
)

val ColorScheme.rpSurfaces: RpSurfaceTokens
    get() {
        val isDark = background.luminance() < 0.5f
        return RpSurfaceTokens(
            elevatedDeck = lerp(surface, surfaceVariant, if (isDark) 0.30f else 0.24f),
            insetBlock = lerp(surface, background, if (isDark) 0.42f else 0.35f),
            hairline = outlineVariant.copy(alpha = if (isDark) 0.45f else 0.38f),
        )
    }

/** Tokens de resplandor / glow derivados del colorScheme. */
data class RpGlowTokens(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
)

val ColorScheme.rpGlows: RpGlowTokens
    @Composable
    @ReadOnlyComposable
    get() =
        RpGlowTokens(
            primary = primary.copy(alpha = 0.35f),
            secondary = secondary.copy(alpha = 0.28f),
            tertiary = tertiary.copy(alpha = 0.25f),
            surface = surfaceVariant.copy(alpha = 0.20f),
        )

/** Gradientes sutiles derivados del tema actual. */
data class RpGradientTokens(
    val surfaceCard: Brush,
    val primaryAccent: Brush,
    val subtleSurface: Brush,
    val shimmer: Brush,
    val workingPulse: Brush,
)

/** Tokens de skeleton de alto contraste derivados del ColorScheme para temas claros y oscuros. */
data class RpSkeletonTokens(
    val base: Color,
    val highlight: Color,
)

val ColorScheme.rpSkeleton: RpSkeletonTokens
    @Composable
    @ReadOnlyComposable
    get() =
        RpSkeletonTokens(
            base = lerp(surfaceVariant, onSurface, 0.14f),
            highlight = lerp(surfaceVariant, onSurface, 0.28f),
        )

val ColorScheme.rpGradients: RpGradientTokens
    @Composable
    @ReadOnlyComposable
    get() =
        RpGradientTokens(
            surfaceCard =
                Brush.verticalGradient(
                    listOf(
                        surface,
                        surfaceVariant.copy(alpha = 0.45f),
                    ),
                ),
            primaryAccent =
                Brush.horizontalGradient(
                    listOf(
                        primary,
                        secondary,
                    ),
                ),
            subtleSurface =
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = 0.95f),
                        background.copy(alpha = 0.85f),
                    ),
                ),
            shimmer =
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.12f),
                        secondary.copy(alpha = 0.30f),
                        primary.copy(alpha = 0.12f),
                    ),
                ),
            workingPulse =
                Brush.radialGradient(
                    listOf(
                        primary.copy(alpha = 0.35f),
                        primary.copy(alpha = 0.0f),
                    ),
                ),
        )
