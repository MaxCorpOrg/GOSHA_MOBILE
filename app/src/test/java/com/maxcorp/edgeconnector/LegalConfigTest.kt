package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalConfigTest {
    @Test
    fun `release legal url policy rejects missing and invalid values`() {
        assertFalse(isHttpUrl(""))
        assertFalse(isHttpUrl("http://"))
        assertFalse(isHttpUrl("legal.example.test/privacy"))
        assertFalse(isHttpUrl("ftp://legal.example.test/privacy"))
    }

    @Test
    fun `legal url policy accepts explicit http urls`() {
        assertTrue(isHttpUrl("https://legal.example.test/privacy"))
        assertTrue(isHttpUrl("http://legal.example.test/terms"))
    }

    @Test
    fun `release runtime policy rejects blank panel endpoint even when legal urls are valid`() {
        assertFalse(isHttpUrl(""))
        assertTrue(isHttpUrl("https://legal.example.test/privacy"))
        assertTrue(isHttpUrl("https://legal.example.test/terms"))
    }

    @Test
    fun `debug default panel endpoint remains blank without release config`() {
        assertEquals("", runtimeDefaultPanelBaseUrl())
    }

    @Test
    fun `debug defaults leave legal documents unconfigured without release config`() {
        assertFalse(PrivacyPolicy.isConfigured())
        assertFalse(TermsOfUse.isConfigured())
    }
}
