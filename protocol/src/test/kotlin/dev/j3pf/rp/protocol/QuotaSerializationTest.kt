package dev.j3pf.rp.protocol

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuotaSerializationTest {
    @Test
    fun `machine summary decodes all quota fields`() {
        val summary =
            RpJson.decodeFromString(
                MachineSummary.serializer(),
                """
                {
                  "machineId": "machine-1",
                  "name": "Development",
                  "quotas": [
                    {
                      "family": "claude",
                      "label": "Claude",
                      "plan": "pro",
                      "ok": false,
                      "notConfigured": true,
                      "error": "credentials expired",
                      "primaryWindowId": "weekly",
                      "windows": [
                        {
                          "id": "weekly",
                          "label": "Weekly",
                          "usedPercent": 37.5,
                          "remainingPercent": 62.5,
                          "resetsAt": "2025-01-08T00:00:00Z",
                          "resetsInSec": 3600,
                          "note": "resets soon"
                        }
                      ],
                      "updatedAt": "2025-01-07T23:00:00Z"
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                MachineQuota(
                    family = "claude",
                    label = "Claude",
                    plan = "pro",
                    ok = false,
                    notConfigured = true,
                    error = "credentials expired",
                    primaryWindowId = "weekly",
                    windows =
                        listOf(
                            QuotaWindow(
                                id = "weekly",
                                label = "Weekly",
                                usedPercent = 37.5,
                                remainingPercent = 62.5,
                                resetsAt = "2025-01-08T00:00:00Z",
                                resetsInSec = 3600,
                                note = "resets soon",
                            ),
                        ),
                    updatedAt = "2025-01-07T23:00:00Z",
                ),
            ),
            summary.quotas,
        )
    }

    @Test
    fun `legacy machine summary decodes without quotas`() {
        val summary =
            RpJson.decodeFromString(
                MachineSummary.serializer(),
                """{"machineId":"machine-legacy","name":"Legacy machine","online":true}""",
            )

        assertEquals("machine-legacy", summary.machineId)
        assertTrue(summary.online)
        assertEquals(null, summary.quotas)
    }

    @Test
    fun `machine summary with quotas serializes and round trips`() {
        val summary =
            MachineSummary(
                machineId = "machine-1",
                name = "Development",
                quotas =
                    listOf(
                        MachineQuota(
                            family = "codex",
                            label = "Codex",
                            windows = listOf(QuotaWindow(id = "daily", usedPercent = 12.0)),
                        ),
                    ),
            )

        val encoded = RpJson.encodeToString(MachineSummary.serializer(), summary)
        val encodedObject = RpJson.parseToJsonElement(encoded).jsonObject

        assertTrue(encodedObject.containsKey("quotas"))
        assertFalse(encodedObject["quotas"]!!.jsonArray.isEmpty())
        assertEquals(
            "codex",
            encodedObject["quotas"]!!
                .jsonArray[0]
                .jsonObject["family"]!!
                .jsonPrimitive.content,
        )
        assertEquals(summary, RpJson.decodeFromString(MachineSummary.serializer(), encoded))
    }
}
