package com.maxcorp.gosha.mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelOnlyStatusTextTest {
    @Test
    fun `panel only menu text does not promise a new local search`() {
        val strings = loadStringResources()
        val panelOnlyResources = listOf(
            "menu_status_platform_only",
            "wifi_robot_resolving_address",
            "wifi_robot_connected_platform",
            "wifi_reconnect_status_platform_only",
        )

        panelOnlyResources.forEach { name ->
            val value = strings.getValue(name)
            assertTrue(
                "$name must say that the local address is not confirmed",
                value.contains("Локальный адрес не подтверждён"),
            )
            assertFalse(
                "$name must not say that the address is being searched now",
                value.contains("Ищу", ignoreCase = true) &&
                    value.contains("локальн", ignoreCase = true),
            )
            assertFalse(
                "$name must not promise automatic address resolution",
                value.contains("уточняется автоматически", ignoreCase = true),
            )
        }
    }

    private fun loadStringResources(): Map<String, String> {
        val stringsFile = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).first(File::exists)

        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(stringsFile)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                put(name, node.textContent)
            }
        }
    }
}
