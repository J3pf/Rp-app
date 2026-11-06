package dev.j3pf.rp.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.inMemoryDb
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
class DaoDedupStoreTest {
    private lateinit var db: dev.j3pf.rp.data.db.RpDatabase
    private lateinit var store: DaoDedupStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = inMemoryDb(context)
        store = DaoDedupStore(db.dedup())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstSeenClaimsEachKeyOnlyOnceWithinTheWindow() = runTest {
        assertTrue(store.firstSeen("prompt:one", now = 1_000L))
        assertFalse(store.firstSeen("prompt:one", now = 2_000L))
        assertTrue(store.firstSeen("prompt:two", now = 2_000L))
    }
}
