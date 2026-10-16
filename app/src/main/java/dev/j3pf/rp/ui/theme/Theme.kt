package dev.j3pf.rp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary

/** Opciones de tema disponibles en RP sincronizados con Pi / Gentle Shell. */
enum class RpThemeChoice(
    val id: String,
    val label: String,
) {
    AUTO("auto", "Sincronizado con Pi (automático)"),
    GENTLE_CUTE("gentle-cute", "Gentle-Cute"),
    GENTLE_SEXY("gentle-sexy", "Gentle-Sexy"),
    OMARCHY("omarchy-system", "Omarchy / Gentle Shell"),
    SYSTEM("system", "Material You / Sistema"),
}

// ───────────────────────────── Paleta Gentle-Cute ─────────────────────────────

internal val CuteLightColors =
    lightColorScheme(
        primary = Color(0xFFD81B60),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD8E4),
        onPrimaryContainer = Color(0xFF3B071E),
        secondary = Color(0xFF8E24AA),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3D8FF),
        onSecondaryContainer = Color(0xFF32004B),
        tertiary = Color(0xFFFF6F00),
        background = Color(0xFFFFF7F9),
        surface = Color(0xFFFFF0F5),
        surfaceVariant = Color(0xFFF8BBD0),
        onBackground = Color(0xFF231A1E),
        onSurface = Color(0xFF231A1E),
        outline = Color(0xFF8A717B),
    )

internal val CuteDarkColors =
    darkColorScheme(
        primary = Color(0xFFFF80AB),
        onPrimary = Color(0xFF560027),
        primaryContainer = Color(0xFF7B003A),
        onPrimaryContainer = Color(0xFFFFD8E4),
        secondary = Color(0xFFCE93D8),
        onSecondary = Color(0xFF38004D),
        secondaryContainer = Color(0xFF52006E),
        onSecondaryContainer = Color(0xFFF3DAFF),
        tertiary = Color(0xFFFFB74D),
        background = Color(0xFF1E171E),
        surface = Color(0xFF281E29),
        surfaceVariant = Color(0xFF3E2D3F),
        onBackground = Color(0xFFF0E5EC),
        onSurface = Color(0xFFF0E5EC),
        outline = Color(0xFF8E7A8A),
    )

// ───────────────────────────── Paleta Gentle-Sexy ─────────────────────────────

internal val SexyLightColors =
    lightColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD8E6),
        onPrimaryContainer = Color(0xFF3E001B),
        secondary = Color(0xFF7B1FA2),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEADBFF),
        onSecondaryContainer = Color(0xFF2C0043),
        tertiary = Color(0xFF0097A7),
        background = Color(0xFFFDF2F8),
        surface = Color(0xFFFCE7F3),
        surfaceVariant = Color(0xFFF48FB1),
        onBackground = Color(0xFF221820),
        onSurface = Color(0xFF221820),
        outline = Color(0xFF89677B),
    )

internal val SexyDarkColors =
    darkColorScheme(
        primary = Color(0xFFFF2A85),
        onPrimary = Color(0xFF4A0022),
        primaryContainer = Color(0xFF700037),
        onPrimaryContainer = Color(0xFFFFD8E6),
        secondary = Color(0xFFB388FF),
        onSecondary = Color(0xFF2E006A),
        secondaryContainer = Color(0xFF4A0099),
        onSecondaryContainer = Color(0xFFEADBFF),
        tertiary = Color(0xFF00E5FF),
        background = Color(0xFF120815),
        surface = Color(0xFF1B0D20),
        surfaceVariant = Color(0xFF2E1336),
        onBackground = Color(0xFFFCE4F0),
        onSurface = Color(0xFFFCE4F0),
        outline = Color(0xFF914F7F),
    )

// ───────────────────────────── Paleta Omarchy / Gentle Shell ─────────────────────────────

internal val OmarchyLightColors =
    lightColorScheme(
        primary = Color(0xFF3D59A1),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF6B4CA3),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE9DCFF),
        tertiary = Color(0xFF0F7B99),
        background = Color(0xFFF5F6FA),
        surface = Color(0xFFE8EBF5),
        surfaceVariant = Color(0xFFD3D8E8),
        onBackground = Color(0xFF191C22),
        onSurface = Color(0xFF191C22),
        error = Color(0xFFBA1A1A),
        outline = Color(0xFF747783),
    )

internal val OmarchyDarkColors =
    darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF152238),
        primaryContainer = Color(0xFF2A3A60),
        onPrimaryContainer = Color(0xFFC0CAF5),
        secondary = Color(0xFFBB9AF7),
        onSecondary = Color(0xFF2B174F),
        secondaryContainer = Color(0xFF432C6E),
        onSecondaryContainer = Color(0xFFEADBFF),
        tertiary = Color(0xFF7DCFFF),
        background = Color(0xFF1A1B26),
        surface = Color(0xFF232431),
        surfaceVariant = Color(0xFF282A38),
        onBackground = Color(0xFFA9B1D6),
        onSurface = Color(0xFFC0CAF5),
        error = Color(0xFFF7768E),
        outline = Color(0xFF45485B),
    )

// ───────────────────────────── Paleta Material You / Default ─────────────────────────────

internal val DefaultLightColors =
    lightColorScheme(
        primary = Color(0xFF4F378B),
        secondary = Color(0xFF625B71),
        tertiary = Color(0xFF7D5260),
    )

internal val DefaultDarkColors =
    darkColorScheme(
        primary = Color(0xFFCFBCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8),
    )

/** Obtiene la paleta estática de un tema dado. */
fun paletteFor(
    themeChoice: RpThemeChoice,
    darkTheme: Boolean,
): ColorScheme =
    when (themeChoice) {
        RpThemeChoice.GENTLE_CUTE -> if (darkTheme) CuteDarkColors else CuteLightColors
        RpThemeChoice.GENTLE_SEXY -> if (darkTheme) SexyDarkColors else SexyLightColors
        RpThemeChoice.OMARCHY -> if (darkTheme) OmarchyDarkColors else OmarchyLightColors
        RpThemeChoice.SYSTEM, RpThemeChoice.AUTO -> if (darkTheme) DefaultDarkColors else DefaultLightColors
    }

/**
 * Resuelve el tema activo según la regla de negocio:
 * "si hay 2 pcs o mas, se toma el del pc personal o el que mas se repita"
 */
fun resolveThemeChoice(
    themeSetting: String,
    machines: List<MachineSummary>,
    agents: List<AgentSummary>,
): RpThemeChoice {
    val norm = themeSetting.lowercase().trim()
    if (norm.isNotEmpty() && norm != "auto") {
        return when (norm) {
            "gentle-cute", "gentleman-cute", "cute" -> RpThemeChoice.GENTLE_CUTE
            "gentle-sexy", "gentleman-sexy", "sexy" -> RpThemeChoice.GENTLE_SEXY
            "omarchy", "omarchy-system", "gentle-shell" -> RpThemeChoice.OMARCHY
            "system", "default" -> RpThemeChoice.SYSTEM
            else -> RpThemeChoice.GENTLE_CUTE
        }
    }

    val onlineMachines = machines.filter { it.online }
    val candidateMachines = if (onlineMachines.isNotEmpty()) onlineMachines else machines

    if (candidateMachines.size >= 2) {
        val personal = candidateMachines.find { it.context.equals("personal", ignoreCase = true) }
        if (personal != null) {
            return themeForMachine(personal, agents)
        }
        val themes = candidateMachines.map { themeForMachine(it, agents) }
        val mostFrequent =
            themes
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        if (mostFrequent != null) return mostFrequent
    } else if (candidateMachines.isNotEmpty()) {
        return themeForMachine(candidateMachines.first(), agents)
    }

    return RpThemeChoice.GENTLE_CUTE
}

private fun themeForMachine(
    m: MachineSummary,
    agents: List<AgentSummary>,
): RpThemeChoice {
    val machineAgents = agents.filter { it.machineId == m.machineId }
    val themesFromAgents =
        machineAgents.mapNotNull { a ->
            val text = "${a.theme} ${a.sessionName} ${a.gentleMode} ${a.launcher}".lowercase()
            when {
                text.contains("sexy") -> RpThemeChoice.GENTLE_SEXY
                text.contains("cute") -> RpThemeChoice.GENTLE_CUTE
                text.contains("omarchy") -> RpThemeChoice.OMARCHY
                else -> null
            }
        }
    if (themesFromAgents.isNotEmpty()) {
        return themesFromAgents
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: RpThemeChoice.GENTLE_CUTE
    }

    val col = m.color.lowercase()
    if (col.contains("e91e63") || col.contains("ff4081") || col.contains("ff2a85")) return RpThemeChoice.GENTLE_SEXY
    if (col.contains("f06292") || col.contains("f48fb1") || col.contains("ce93d8")) return RpThemeChoice.GENTLE_CUTE

    return RpThemeChoice.GENTLE_CUTE
}

/** Tema principal de RP con soporte para temas de Pi/Gentle Shell (Gentle-Cute, Gentle-Sexy, Omarchy). */
@Composable
fun RpTheme(
    themeChoice: RpThemeChoice = RpThemeChoice.AUTO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme: ColorScheme =
        when (themeChoice) {
            RpThemeChoice.GENTLE_CUTE -> {
                if (darkTheme) CuteDarkColors else CuteLightColors
            }

            RpThemeChoice.GENTLE_SEXY -> {
                if (darkTheme) SexyDarkColors else SexyLightColors
            }

            RpThemeChoice.OMARCHY -> {
                if (darkTheme) OmarchyDarkColors else OmarchyLightColors
            }

            RpThemeChoice.SYSTEM, RpThemeChoice.AUTO -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                } else {
                    if (darkTheme) DefaultDarkColors else DefaultLightColors
                }
            }
        }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colores semánticos que no existen en Material (peligro, éxito). */
object RpColors {
    val danger = Color(0xFFB3261E)
    val warning = Color(0xFFB26A00)
    val success = Color(0xFF2E7D32)
}
