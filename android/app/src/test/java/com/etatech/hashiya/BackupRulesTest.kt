package com.etatech.hashiya

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** section name → "domain:path" of each include in it. */
    private fun includes(xml: Int): Map<String, List<String>> {
        val parser = context.resources.getXml(xml)
        val result = mutableMapOf<String, MutableList<String>>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer", "full-backup-content" -> section = parser.name
                "include" -> result.getOrPut(section) { mutableListOf() } +=
                    "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
            }
        }
        return result
    }

    private val core = listOf(
        "database:.",
        "file:datastore/",
        "file:androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"
    )

    @Test
    fun cloudBackupLeavesPdfsOut() {
        val rules = includes(R.xml.data_extraction_rules)
        assertEquals(core, rules["cloud-backup"])
        assertEquals(core + "file:pdfs/", rules["device-transfer"])
    }

    @Test
    fun legacyBackupMatchesCloud() {
        assertEquals(core, includes(R.xml.backup_rules)["full-backup-content"])
    }

    /** The value of [attribute] on each start tag that has it, as "tag[domain:path]" or "tag" → value. */
    private fun attributes(xml: Int, attribute: String): Map<String, String> {
        val parser = context.resources.getXml(xml)
        val result = mutableMapOf<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val value = parser.getAttributeValue(null, attribute) ?: continue
            val path = parser.getAttributeValue(null, "path")
            result[if (path == null) parser.name else "${parser.name}[${parser.getAttributeValue(null, "domain")}:$path]"] = value
        }
        return result
    }

    @Test
    fun theApiKeyIsOnlyBackedUpEncrypted() {
        // The settings file holds the API key.
        assertEquals(mapOf("cloud-backup" to "true"), attributes(R.xml.data_extraction_rules, "disableIfNoEncryptionCapabilities"))
        assertEquals(mapOf("include[file:datastore/]" to "clientSideEncryption"), attributes(R.xml.backup_rules, "requireFlags"))
    }
}
