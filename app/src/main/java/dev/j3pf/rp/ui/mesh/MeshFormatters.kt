package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.protocol.MeshNode

fun formatMeshRole(role: String): String =
    when (role.lowercase()) {
        "orchestrator" -> "Orquestador"
        "subordinate" -> "Subordinado"
        "solo" -> "Individual"
        else -> "Agente"
    }

fun formatMeshStatus(status: String): String =
    when (status.lowercase()) {
        "working" -> "Trabajando"
        "waiting_input" -> "Esperando respuesta"
        "idle" -> "Inactivo"
        "offline" -> "Desconectado"
        "error" -> "Error"
        "compacting" -> "Compactando"
        else -> status.replace('_', ' ')
    }

fun formatMeshSubagentStatus(status: String): String =
    when (status.lowercase()) {
        "running" -> "En ejecución"
        "working" -> "Trabajando"
        "done" -> "Terminado"
        "completed" -> "Completado"
        "error" -> "Error"
        "stopped" -> "Detenido"
        else -> status.replace('_', ' ')
    }

fun formatMeshCommAction(action: String): String =
    when (action.lowercase()) {
        "ask" -> "Pregunta"
        "reply" -> "Respuesta"
        "handover" -> "Pase"
        "send" -> "Envío"
        "message", "unknown" -> "Mensaje"
        else -> "Mensaje"
    }

fun formatMeshCommText(text: String?): String = text ?: "Contenido oculto"

fun formatMeshTodos(
    done: Int,
    total: Int,
): String = "$done de $total tareas"

fun formatMeshNodeContentDescription(node: MeshNode): String =
    "${formatMeshRole(node.role)} ${node.name}, ${formatMeshStatus(node.status)}" +
        (node.todos?.let { ", ${formatMeshTodos(it.done, it.total)}" } ?: "")
