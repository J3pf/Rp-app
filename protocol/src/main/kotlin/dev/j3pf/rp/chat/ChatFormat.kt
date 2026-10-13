package dev.j3pf.rp.chat

import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str
import kotlinx.serialization.json.JsonElement

/** Resumen de una llamada a herramienta para la tarjeta plegada (bash → comando, edit/read/write → ruta). */
fun ToolRun.summary(maxLen: Int = 120): String {
    val a = args.obj()
    val raw = when (name.lowercase()) {
        "bash" -> a?.str("command")
        "read", "write", "edit", "ls" -> a?.str("path") ?: a?.str("file_path") ?: a?.str("filePath")
        "grep", "find" -> a?.str("pattern")
        else -> a?.entries?.firstOrNull { it.value.str() != null }?.value.str()
    } ?: ""
    val one = raw.replace('\n', ' ').trim()
    return if (one.length > maxLen) one.take(maxLen - 1) + "…" else one
}

/** Par (antes, después) de un `edit` para pintar un diff; `null` si no se puede deducir. */
fun ToolRun.editDiff(): Pair<String, String>? {
    if (name.lowercase() != "edit") return null
    val a = args.obj() ?: return null
    val old = a.str("oldText") ?: a.str("old_string") ?: a.str("oldString")
    val new = a.str("newText") ?: a.str("new_string") ?: a.str("newString")
    if (old != null && new != null) return old to new
    val first = (a["edits"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull().obj()
    val o = first?.str("oldText") ?: return null
    val n = first.str("newText") ?: return null
    return o to n
}

/** Diff lineal sencillo: líneas quitadas con '-' y añadidas con '+'. */
fun simpleDiff(old: String, new: String): List<DiffLine> {
    val o = old.lines()
    val n = new.lines()
    var prefix = 0
    while (prefix < o.size && prefix < n.size && o[prefix] == n[prefix]) prefix++
    var suffix = 0
    while (suffix < o.size - prefix && suffix < n.size - prefix && o[o.size - 1 - suffix] == n[n.size - 1 - suffix]) suffix++
    val out = mutableListOf<DiffLine>()
    o.subList(0, prefix).takeLast(2).forEach { out += DiffLine(DiffKind.SAME, it) }
    o.subList(prefix, o.size - suffix).forEach { out += DiffLine(DiffKind.REMOVED, it) }
    n.subList(prefix, n.size - suffix).forEach { out += DiffLine(DiffKind.ADDED, it) }
    o.subList(o.size - suffix, o.size).take(2).forEach { out += DiffLine(DiffKind.SAME, it) }
    return out
}

enum class DiffKind { SAME, ADDED, REMOVED }
data class DiffLine(val kind: DiffKind, val text: String)

/** Texto del argumento completo para el diálogo de permisos (bash muestra el comando entero). */
fun prettyArgs(args: JsonElement?): String {
    val o = args.obj() ?: return args?.toString() ?: ""
    o.str("command")?.let { return it }
    return o.entries.joinToString("\n") { (k, v) -> "$k: ${v.str() ?: v.toString()}" }
}
