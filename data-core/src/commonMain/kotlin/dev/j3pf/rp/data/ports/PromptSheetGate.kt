package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Port contract for open prompts and answering (Unit P5).
 *
 * Pure delegate: preserves S19-adjacent `"cancelled":true` wire discipline and
 * first-answer-wins (`AlreadyResolved`) semantics without adding confirmation
 * or retry logic at the port layer.
 */
interface PromptSheetGate {
    val open: Flow<List<Prompt>>

    fun openFor(agentId: String): Flow<List<Prompt>> = open.map { list -> list.filter { it.agentId == agentId } }.distinctUntilChanged()

    fun observe(promptId: String): Flow<Prompt?> = open.map { list -> list.firstOrNull { it.promptId == promptId } }.distinctUntilChanged()

    fun start()

    suspend fun clear()

    suspend fun refresh()

    suspend fun answer(
        promptId: String,
        answer: Answer,
    ): AnswerOutcome
}
