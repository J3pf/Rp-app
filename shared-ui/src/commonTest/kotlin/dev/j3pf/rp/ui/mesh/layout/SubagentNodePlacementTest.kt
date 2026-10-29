package dev.j3pf.rp.ui.mesh.layout

import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshSubagent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regla de la Fase D: un subagente **en ejecución** es un nodo real del diagrama, un nivel por debajo
 * de su agente; uno **terminado** se queda en la lista compacta de píldoras. La misma identidad no
 * puede estar en los dos sitios a la vez.
 *
 * Se prueba a través de `computeLayout`, que es la interfaz pública del motor: los nodos sintéticos se
 * reconocen porque no estaban en la entrada, no porque el test conozca el formato de su id.
 */
class SubagentNodePlacementTest {
    private val engine = MeshLayoutEngine()

    @Test
    fun `un subagente en ejecucion es un nodo hijo de su agente`() {
        val agente = agent("a", listOf(sub("s1", "running")))
        val resultado = engine.computeLayout(listOf(agente), emptyList(), width = 360f, height = 800f)

        val sinteticos = sinteticos(resultado, setOf("a"))
        assertEquals(1, sinteticos.size, "un subagente en ejecución debe aparecer como nodo")
        val nodo = sinteticos.single()
        assertEquals("a", nodo.parentId, "el nodo del subagente cuelga de su agente")
        assertEquals(
            resultado.nodes.getValue("a").level + 1,
            nodo.level,
            "el subagente vive exactamente un nivel por debajo de su agente",
        )
    }

    @Test
    fun `la tabla de estados decide nodo o pill sin inventar actividad`() {
        val casos =
            listOf(
                "running" to 1,
                "working" to 1,
                "RUNNING" to 1,
                " Working " to 1,
                "done" to 0,
                "completed" to 0,
                "error" to 0,
                "stopped" to 0,
                "queued" to 0,
                "desconocido" to 0,
                "" to 0,
            )
        for ((estado, esperados) in casos) {
            val resultado =
                engine.computeLayout(
                    listOf(agent("a", listOf(sub("s1", estado)))),
                    emptyList(),
                    width = 360f,
                    height = 800f,
                )
            assertEquals(
                esperados,
                sinteticos(resultado, setOf("a")).size,
                "estado '$estado' debería producir $esperados nodo(s) de subagente",
            )
        }
    }

    @Test
    fun `al terminar, el subagente deja de ser nodo y no queda duplicado`() {
        val enEjecucion = engine.computeLayout(listOf(agent("a", listOf(sub("s1", "running")))), emptyList())
        val terminado = engine.computeLayout(listOf(agent("a", listOf(sub("s1", "done")))), emptyList())

        val antes = sinteticos(enEjecucion, setOf("a"))
        val despues = sinteticos(terminado, setOf("a"))
        assertEquals(1, antes.size)
        assertEquals(0, despues.size, "un subagente terminado vuelve a la píldora, no al diagrama")
        assertEquals(
            0,
            terminado.nodes.keys.count { it == antes.single().node.agentId },
            "la misma identidad no puede quedar posicionada después de terminar",
        )
    }

    @Test
    fun `un agente sin subagentes no produce nodos de mas`() {
        val resultado = engine.computeLayout(listOf(agent("a")), emptyList())
        assertEquals(emptyList(), sinteticos(resultado, setOf("a")))
        assertEquals(1, resultado.nodes.size, "sin subagentes solo está el agente")
    }

    @Test
    fun `dos subagentes en ejecucion no se solapan a 360dp`() {
        val resultado =
            engine.computeLayout(
                listOf(agent("a", listOf(sub("s1", "running"), sub("s2", "working")))),
                emptyList(),
                width = 360f,
                height = 800f,
            )
        val posicionados = resultado.nodes.values.toList()
        for (i in posicionados.indices) {
            for (j in i + 1 until posicionados.size) {
                val uno = posicionados[i]
                val otro = posicionados[j]
                val separados =
                    uno.right <= otro.left + 0.1f ||
                        otro.right <= uno.left + 0.1f ||
                        uno.bottom <= otro.top + 0.1f ||
                        otro.bottom <= uno.top + 0.1f
                assertTrue(
                    separados,
                    "${uno.node.agentId} se solapa con ${otro.node.agentId} a 360dp",
                )
            }
        }
    }

    @Test
    fun `el nodo del subagente muestra su etiqueta y nunca su id crudo`() {
        val tarea = MeshSubagent(taskId = "0f3a9b21-aa11", agent = "agent-x", label = "Revisar PR", status = "running")
        val resultado = engine.computeLayout(listOf(agent("a", listOf(tarea))), emptyList())

        val nodo = sinteticos(resultado, setOf("a")).single().node
        assertEquals("Revisar PR", nodo.name)
        assertFalse(nodo.name.contains("0f3a"), "el id de la tarea no se pinta: ${nodo.name}")
        assertFalse(nodo.agentId.contains("0f3a9b21-aa11"), "el id interno del nodo no expone el taskId")
    }

    private fun sinteticos(
        resultado: MeshLayoutResult,
        idsOriginales: Set<String>,
    ): List<PositionedNode> = resultado.nodes.values.filter { it.node.agentId !in idsOriginales }

    private fun agent(
        id: String,
        subs: List<MeshSubagent> = emptyList(),
    ) = MeshNode(
        agentId = id,
        machineId = "machine",
        machineName = "Machine",
        name = id,
        status = "working",
        role = "solo",
        subagents = subs,
    )

    private fun sub(
        id: String,
        status: String,
        label: String? = id,
    ) = MeshSubagent(taskId = id, agent = "agent-$id", label = label, status = status)
}
