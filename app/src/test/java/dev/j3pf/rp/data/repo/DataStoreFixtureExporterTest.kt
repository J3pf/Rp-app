package dev.j3pf.rp.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Exports a real Preferences DataStore file for the S14 upgrade-fixture proof. */
class DataStoreFixtureExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun productionSettersPersistFixtureValuesAndOptionallyExportBytes() = runTest {
        val file = File(tmp.root, "rp_settings.preferences_pb")
        val store =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        val repo = SettingsRepository(store)
        val notifyPrefs =
            NotifyPrefs(
                categories = mapOf("approvals" to false, "questions" to true),
                machines =
                    mapOf(
                        "machine-alpha" to
                            MachinePrefs(
                                mute = true,
                                categories = mapOf("terminal" to false),
                                quietHours = QuietHours(from = "22:00", to = "07:00", tz = "Europe/Madrid"),
                            ),
                    ),
            )

        repo.setAppTheme("dark")
        repo.setChatFontScale(1.2f)
        repo.setChatMessageSpacing("compact")
        repo.setAgentMuted("agent-beta", true)
        repo.setSelectedContext("personal")
        repo.setRequireUnlock(false)
        repo.setFlagSecure(true)
        repo.setAppLock(true)
        repo.setOnboardingDone(true)
        repo.setNotifyPrefs(notifyPrefs)

        val settings = repo.current()
        assertEquals("dark", settings.appTheme)
        assertEquals(1.2f, settings.chatFontScale)
        assertEquals("compact", settings.chatMessageSpacing)
        assertEquals(setOf("agent-beta"), settings.mutedAgents)
        assertEquals("personal", settings.selectedContext)
        assertEquals(false, settings.requireUnlockToApprove)
        assertEquals(true, settings.flagSecure)
        assertEquals(true, settings.appLock)
        assertEquals(true, settings.onboardingDone)
        assertEquals(notifyPrefs, settings.notifyPrefs)

        val exported = System.getProperty("parity.dsfixture.out")?.also { outputPath ->
            val output = File(outputPath)
            output.parentFile?.mkdirs()
            file.copyTo(output, overwrite = true)
        }
    }
}
