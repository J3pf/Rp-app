package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshSubagent

/** Las dos únicas representaciones que admite un subagente en la malla. */
enum class SubagentSlot {
    /** En ejecución: nodo propio, un nivel por debajo de su agente. */
    NODE,

    /** Terminado o sin estado reconocible: píldora compacta en la tarjeta del agente. */
    PILL,
}

/**
 * Regla de la Fase D, aislada del motor para poder tablearla sin construir una malla entera.
 *
 * Solo Pi y agy emiten eventos de subagente; claude y codex no tienen fuente. Por eso un estado que no
 * consta como activo **no** se convierte en nodo: inventar actividad donde no la hay es peor que mostrar
 * una píldora de más. Un subagente desconocido se muestra, pero como terminado.
 */
object SubagentPlacement {
    private val ACTIVOS = setOf("running", "working")

    /** El sufijo del id es estable por tarea, pero no es el `taskId`: el diagrama nunca pinta ids crudos. */
    private fun huella(taskId: String): String = taskId.hashCode().toUInt().toString(16)

    fun slot(status: String): SubagentSlot {
        val normalizado = status.trim().lowercase()
        return if (normalizado in ACTIVOS) SubagentSlot.NODE else SubagentSlot.PILL
    }

    /** Devuelve `activos` (van al diagrama) y `terminados` (se quedan en píldoras). */
    fun split(subagentes: List<MeshSubagent>): Pair<List<MeshSubagent>, List<MeshSubagent>> {
        val (activos, terminados) = subagentes.partition { slot(it.status) == SubagentSlot.NODE }
        return activos to terminados
    }

    /** Id interno y determinista del nodo; no contiene el `taskId` para que no pueda filtrarse. */
    fun nodeId(
        parentAgentId: String,
        taskId: String,
    ): String = "sub:$parentAgentId:${huella(taskId)}"

    /** Lo que se pinta: la etiqueta si existe, si no el nombre del agente que ejecuta la tarea. */
    fun displayName(subagente: MeshSubagent): String {
        val etiqueta = subagente.label?.trim()
        return if (etiqueta.isNullOrEmpty()) subagente.agent else etiqueta
    }
}
