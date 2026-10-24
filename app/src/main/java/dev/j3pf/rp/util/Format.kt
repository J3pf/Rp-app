package dev.j3pf.rp.util

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.ui.util.agentTitle as sharedAgentTitle
import dev.j3pf.rp.ui.util.contextEmoji as sharedContextEmoji
import dev.j3pf.rp.ui.util.contextLabel as sharedContextLabel
import dev.j3pf.rp.ui.util.humanSize as sharedHumanSize
import dev.j3pf.rp.ui.util.lastSegment as sharedLastSegment
import dev.j3pf.rp.ui.util.parseColor as sharedParseColor
import dev.j3pf.rp.ui.util.relativeTime as sharedRelativeTime
import dev.j3pf.rp.ui.util.stableId as sharedStableId
import dev.j3pf.rp.ui.util.truncate as sharedTruncate

/** Último segmento de una ruta (acepta `/` y `\`). */
fun lastSegment(path: String?): String = sharedLastSegment(path)

/** "hace 3 h", "hace 5 min", "ahora". */
fun relativeTime(
    thenMs: Long?,
    nowMs: Long = System.currentTimeMillis(),
): String = sharedRelativeTime(thenMs, nowMs)

fun humanSize(bytes: Long): String = sharedHumanSize(bytes)

/** `#RRGGBB` → [Color]; color por defecto si el valor es inválido. */
fun parseColor(
    hex: String?,
    fallback: Color = Color(0xFF6750A4),
): Color = sharedParseColor(hex, fallback)

fun contextLabel(context: String): String = sharedContextLabel(context)

fun contextEmoji(context: String): String = sharedContextEmoji(context)

/** "api-gateway" etc. — nombre visible del agente. */
fun agentTitle(
    sessionName: String?,
    cwd: String?,
    agentId: String,
): String = sharedAgentTitle(sessionName, cwd, agentId)

/** Hash estable y positivo para ids de notificación. */
fun stableId(s: String): Int = sharedStableId(s)

fun truncate(
    s: String,
    max: Int,
): String = sharedTruncate(s, max)
