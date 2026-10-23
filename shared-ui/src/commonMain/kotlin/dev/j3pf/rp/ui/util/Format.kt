package dev.j3pf.rp.ui.util

import androidx.compose.ui.graphics.Color

/** Último segmento de una ruta (acepta `/` y `\`). */
fun lastSegment(path: String?): String {
    if (path.isNullOrBlank()) return ""
    return path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
}

/** "hace 3 h", "hace 5 min", "ahora". */
fun relativeTime(
    thenMs: Long?,
    nowMs: Long = currentTimeMillis(),
): String {
    if (thenMs == null || thenMs <= 0) return ""
    val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
    return when {
        s < 45 -> "ahora"
        s < 3600 -> "hace ${(s / 60).coerceAtLeast(1)} min"
        s < 86_400 -> "hace ${s / 3600} h"
        else -> "hace ${s / 86_400} d"
    }
}

fun humanSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

/** `#RRGGBB` → [Color]; color por defecto si el valor es inválido. */
fun parseColor(
    hex: String?,
    fallback: Color = Color(0xFF6750A4),
): Color =
    runCatching {
        val clean = (hex ?: return fallback).trim().removePrefix("#")
        val colorLong = clean.toLong(16)
        if (clean.length <= 6) {
            Color(colorLong or 0xFF000000)
        } else {
            Color(colorLong)
        }
    }.getOrDefault(fallback)

fun contextLabel(context: String): String =
    when (context) {
        "personal" -> "Personal"
        "work" -> "Empresa"
        else -> context.replaceFirstChar { it.uppercase() }
    }

fun contextEmoji(context: String): String = ""

/** "api-gateway" etc. — nombre visible del agente. */
fun agentTitle(
    sessionName: String?,
    cwd: String?,
    agentId: String,
): String = sessionName?.takeIf { it.isNotBlank() } ?: lastSegment(cwd).ifEmpty { agentId.substringAfter(':').take(8) }

/** Hash estable y positivo para ids de notificación. */
fun stableId(s: String): Int = s.hashCode() and 0x7fffffff

fun truncate(
    s: String,
    max: Int,
): String = if (s.length <= max) s else s.take(max - 1) + "…"
