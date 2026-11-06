package dev.j3pf.rp.push

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Robolectric characterization of boot/package-replacement routing on unchanged Android code (T1; S5/S11/S15).
 * The enabled-start assertion is omitted: Robolectric 4.17 recorded no start intent on API 34 or 32, while a recording
 * Application is rejected by the Hilt BroadcastReceiver. DirectPushService.start wraps the platform call in runCatching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {

    private data class Fixture(
        val context: Application,
        val receiver: BootReceiver,
        val appScope: CoroutineScope,
        val dataStoreScope: CoroutineScope,
        val settingsFile: File,
    )

    private lateinit var fixture: Fixture

    @After
    fun tearDown() {
        if (::fixture.isInitialized) {
            fixture.context.unregisterReceiver(fixture.receiver)
            fixture.appScope.cancel()
            fixture.dataStoreScope.cancel()
            fixture.settingsFile.delete()
        }
    }

    private suspend fun createFixture(
        directPush: Boolean,
        scheduler: TestCoroutineScheduler,
    ): Fixture {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsFile = File.createTempFile("boot-receiver", ".preferences_pb").also { it.delete() }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = dataStoreScope, produceFile = { settingsFile }))
        settings.setDirectPush(directPush)
        settings.current() // Ensure the controlled receiver coroutine reads the initialized setting.
        val appScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))
        val secrets: SecretStore = FakeSecretStore(context, testSession("https://relay.example"))
        val receiver = BootReceiver()
        inject(receiver, "secrets", secrets)
        inject(receiver, "settings", settings)
        inject(receiver, "scope", appScope)

        // The manifest already contributes an uninjected BootReceiver instance in Robolectric. Keep
        // the injected instance below as the sole test recipient; Android still supplies its PendingResult.
        shadowOf(context).clearRegisteredReceivers()
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BOOT_COMPLETED)
                addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                addAction("dev.j3pf.rp.TEST_OTHER_ACTION")
            },
            android.content.Context.RECEIVER_NOT_EXPORTED,
        )
        return Fixture(context, receiver, appScope, dataStoreScope, settingsFile)
    }

    private fun inject(
        receiver: BroadcastReceiver,
        name: String,
        dependency: Any,
    ) {
        receiver.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(receiver, dependency)
        }
    }

    private fun send(action: String) {
        fixture.context.sendBroadcast(Intent(action).setPackage(fixture.context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun awaitReceiverWork() {
        val receiverScope = fixture.appScope.coroutineContext[Job] ?: return
        receiverScope.children.toList().forEach { it.join() }
    }

    private fun nextStartedService(): Intent? = shadowOf(fixture.context).getNextStartedService()


    @Test
    fun `boot completed does not start direct service when disabled`() = runTest {
        fixture = createFixture(directPush = false, scheduler = testScheduler)
        send(Intent.ACTION_BOOT_COMPLETED)
        awaitReceiverWork()
        advanceUntilIdle()

        assertNull(nextStartedService(), "directPush=false leaves the service queue empty")
    }

    @Test
    fun `unrelated broadcast action is ignored`() = runTest {
        fixture = createFixture(directPush = true, scheduler = testScheduler)
        send("dev.j3pf.rp.TEST_OTHER_ACTION")
        awaitReceiverWork()
        advanceUntilIdle()

        assertNull(nextStartedService(), "an action other than boot or package replacement starts no service")
    }
}
