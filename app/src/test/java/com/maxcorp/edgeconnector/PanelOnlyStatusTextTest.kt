package com.maxcorp.gosha.mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelOnlyStatusTextTest {
    @Test
    fun `panel only text does not overpromise automatic retries`() {
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
                value.contains("Локальный адрес") &&
                    value.contains("не подтверждён"),
            )
            assertFalse(
                "$name must not say that the address is being searched now",
                value.contains("Ищу", ignoreCase = true) &&
                    value.contains("локальн", ignoreCase = true),
            )
        }

        listOf(
            "wifi_robot_resolving_address",
            "wifi_reconnect_status_platform_only",
        ).forEach { name ->
            val value = strings.getValue(name)
            assertFalse(
                "$name is also used by one-shot checks and must not promise background retries",
                value.contains("продолжает автоматическую проверку"),
            )
            assertTrue(
                "$name must offer a truthful next step",
                value.contains("обновите статус"),
            )
        }

        listOf(
            "menu_status_platform_only",
            "wifi_robot_connected_platform",
        ).forEach { name ->
            assertFalse(
                "$name must not promise automatic address resolution outside the retrying menu state",
                strings.getValue(name).contains("автоматическ", ignoreCase = true),
            )
        }

        listOf(
            "wifi_robot_recovery_timed_out",
            "menu_status_recovery_timed_out",
        ).forEach { name ->
            val value = strings.getValue(name)
            assertTrue(
                "$name must explain that automatic checks have finished",
                value.contains("после автоматических проверок"),
            )
            assertFalse(
                "$name must not promise more automatic checks",
                value.contains("продолжает", ignoreCase = true),
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
