package com.example.healthconnectandroid.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupExclusionRulesTest {
    private val allPrivateDomains = setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref"
    )

    @Test
    fun manifestDisablesApplicationBackup() {
        val document = parse("src/main/AndroidManifest.xml")
        val application = document.getElementsByTagName("application").item(0) as Element

        assertEquals(
            "false",
            application.getAttributeNS("http://schemas.android.com/apk/res/android", "allowBackup")
        )
    }

    @Test
    fun legacyBackupRulesExcludeEveryPrivateDomain() {
        val document = parse("src/main/res/xml/backup_rules.xml")
        assertEquals(allPrivateDomains, excludedDomains(document.documentElement))
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeEveryPrivateDomain() {
        val document = parse("src/main/res/xml/data_extraction_rules.xml")
        val cloud = document.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = document.getElementsByTagName("device-transfer").item(0) as Element

        assertEquals(allPrivateDomains, excludedDomains(cloud))
        assertEquals(allPrivateDomains, excludedDomains(transfer))
    }

    private fun excludedDomains(parent: Element): Set<String> {
        val excludes = parent.getElementsByTagName("exclude")
        val domains = buildSet {
            for (index in 0 until excludes.length) {
                val element = excludes.item(index) as Element
                assertEquals(".", element.getAttribute("path"))
                add(element.getAttribute("domain"))
            }
        }
        assertFalse(domains.isEmpty())
        return domains
    }

    private fun parse(relativePath: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(projectFile(relativePath))

    private fun projectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("AndroidClient/app", relativePath)
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Could not locate $relativePath")
    }
}
