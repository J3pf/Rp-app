package dev.j3pf.rp.platform

import dev.j3pf.rp.data.db.PromptDao
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.data.ports.OpenPromptGuard
import dev.j3pf.rp.data.secure.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DaoOpenPromptGuard
    @Inject
    constructor(
        private val promptDao: PromptDao,
        private val secrets: SecretStore,
    ) : OpenPromptGuard {
        override suspend fun isOpen(promptId: String): Boolean {
            val relayId = secrets.current()?.relayId ?: return false
            return promptDao.get(relayId, promptId)?.state == PromptState.OPEN
        }
    }
