package dev.j3pf.rp.platform

import dev.j3pf.rp.data.db.DedupDao
import dev.j3pf.rp.data.ports.DedupStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DaoDedupStore
    @Inject
    constructor(
        private val dao: DedupDao,
    ) : DedupStore {
        override suspend fun firstSeen(
            key: String,
            now: Long,
        ): Boolean = dao.firstSeen(key, now)
    }
