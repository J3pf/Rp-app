package dev.j3pf.rp.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.PromptEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.testSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoOpenPromptGuardTest {
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var guard: DaoOpenPromptGuard

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = inMemoryDb(context)
        val secrets = FakeSecretStore(context, testSession("https://relay.test", relayId = "r1"))
        guard = DaoOpenPromptGuard(db.prompts(), secrets)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun isOpenUsesTheCurrentSessionRelayAndPromptState() = runTest {
        db.prompts().upsert(
            listOf(
                PromptEntity("r1", "open-id", "agent", "{}", "open", 0, 1L),
                PromptEntity("r1", "resolved-id", "agent", "{}", "resolved", 0, 1L),
            ),
        )

        assertTrue(guard.isOpen("open-id"))
        assertFalse(guard.isOpen("resolved-id"))
        assertFalse(guard.isOpen("missing-id"))
    }
}
