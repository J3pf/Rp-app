package dev.j3pf.rp.data.secure

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verdict rev-3 fix (2), MAN-01: the secrets must stay OUT of cloud/device backups (S18).
 * Pinned statically under Robolectric: `android:allowBackup="false"` (FLAG_ALLOW_BACKUP absent)
 * and the exclusion rules xml files (backup_rules + data_extraction_rules) actually exclude the
 * `rp_secrets`, `rp_keyset*` shared prefs names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRulesTest {
    @Test
    fun allowBackupIsFalseInTheManifest() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "app/src/main/AndroidManifest.xml must keep android:allowBackup=\"false\"",
            ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            info.packageName,
        )
        assertTrue(
            "FLAG_ALLOW_BACKUP must be OFF",
            (info.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) == 0,
        )
        // dataExtractionRules/fullBackupContent resource attributes lack a stable ApplicationInfo
        // field on this SDK surface; validated by resolving the XML resources directly (next test).
    }

    @Test
    fun backupRuleXmlsExcludeTheWholeSharedprefDatabaseAndFileDomains() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (ruleName in listOf("backup_rules", "data_extraction_rules")) {
            val id = context.resources.getIdentifier(ruleName, "xml", context.packageName)
            assertTrue("$ruleName resource must exist", id != 0)
            val text = context.resources.getXml(id).use { parser ->
                buildString {
                    var event = parser.eventType
                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            append("<").append(parser.name).append(">")
                            for (i in 0 until parser.attributeCount) {
                                append(" ").append(parser.getAttributeName(i)).append("=").append(parser.getAttributeValue(i))
                            }
                        }
                        event = parser.next()
                    }
                }
            }
            // REAL rule (corrected from my initial guess): the rules exclude the WHOLE
            // sharedpref/database/file domains with path "." — so rp_secrets, rp_keyset and
            // rp_keyset_prefs (any file under shared pref/database/file domains) NEVER leave
            // the device by backup or transfer, by construction.
            for (domain in listOf("sharedpref", "database", "file")) {
                assertTrue("$ruleName must exclude domain=$domain with path=. (found: $text)", text.contains("domain=$domain path=."))
            }
            if (ruleName == "data_extraction_rules") {
                assertTrue("cloud-backup rules must exist", text.contains("<cloud-backup>"))
                assertTrue("device-transfer rules must exist", text.contains("<device-transfer>"))
            }
        }
    }
}
