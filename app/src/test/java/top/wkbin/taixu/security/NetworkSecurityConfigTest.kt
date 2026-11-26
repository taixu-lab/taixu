package top.wkbin.taixu.security

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NetworkSecurityConfigTest {
    @Test fun baseCleartextStaysPermittedWithoutPerDomainRules() {
        val file = networkSecurityConfigFile()
        val text = file.readText()
        assertTrue(text.contains("Found multiple conflicting per-domain rules"))
        assertTrue(text.contains("不要再加 domain-config"))

        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val doc = factory.newDocumentBuilder().parse(file)
        val baseConfigs = doc.getElementsByTagName("base-config")
        assertEquals(1, baseConfigs.length)
        val base = baseConfigs.item(0) as Element
        assertEquals("true", base.getAttribute("cleartextTrafficPermitted"))
        assertEquals(0, doc.getElementsByTagName("domain-config").length)
        assertEquals(0, doc.getElementsByTagName("domain").length)
    }

    private fun networkSecurityConfigFile(): File {
        val relative = "src/main/res/xml/taixu_network_security_config.xml"
        val start = File(System.getProperty("user.dir") ?: ".")
        return generateSequence(start) { it.parentFile }
            .map { dir ->
                listOf(File(dir, relative), File(dir, "app/$relative")).firstOrNull { it.isFile }
            }
            .filterNotNull()
            .firstOrNull()
            ?: error("找不到 taixu_network_security_config.xml，cwd=$start")
    }
}
