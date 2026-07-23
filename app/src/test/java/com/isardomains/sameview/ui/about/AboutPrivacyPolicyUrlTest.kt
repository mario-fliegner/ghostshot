package com.isardomains.sameview.ui.about

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that the About screen's privacy policy URL string resources are
 * correctly localized: the default (English) resource points to /en/privacy
 * and the German resource override points to /de/privacy. Any app language
 * without its own values-<lang>/strings.xml override relies on the standard
 * Android resource fallback to values/strings.xml at runtime; that fallback
 * mechanism itself is a platform guarantee, not app logic, and is not
 * re-verified here.
 */
class AboutPrivacyPolicyUrlTest {

    @Test
    fun defaultResource_hasWebsiteUrl() {
        val url = readStringResource(resDir("values"), "about_website_url")
        assertEquals(
            "https://sameview.app/?utm_source=sameview_android&utm_medium=app&utm_campaign=app_links&utm_content=about_website",
            url
        )
    }

    @Test
    fun defaultResource_hasEnglishPrivacyPolicyUrl() {
        val url = readStringResource(resDir("values"), "about_privacy_policy_url")
        assertEquals(
            "https://sameview.app/en/privacy?utm_source=sameview_android&utm_medium=app&utm_campaign=app_links&utm_content=about_privacy",
            url
        )
    }

    @Test
    fun germanResource_hasGermanPrivacyPolicyUrl() {
        val url = readStringResource(resDir("values-de"), "about_privacy_policy_url")
        assertEquals(
            "https://sameview.app/de/privacy?utm_source=sameview_android&utm_medium=app&utm_campaign=app_links&utm_content=about_privacy",
            url
        )
    }

    private fun resDir(qualifier: String): File {
        val candidates = listOf(
            File("src/main/res/$qualifier"),
            File("app/src/main/res/$qualifier")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate res/$qualifier from working dir ${File(".").absolutePath}")
    }

    private fun readStringResource(dir: File, name: String): String {
        val file = File(dir, "strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
            if (nameAttr == name) {
                return node.textContent
            }
        }
        error("String resource '$name' not found in ${file.path}")
    }
}
