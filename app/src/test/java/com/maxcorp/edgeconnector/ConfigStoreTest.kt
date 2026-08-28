package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigStoreTest {
    @Test
    fun `fresh install does not invent public panel endpoint or connector identity`() {
        val store = ConfigStore(TestSharedPreferences())

        val draft = store.loadDraft()

        assertEquals("", draft.panelBaseUrl)
        assertEquals("", draft.robotId)
        assertEquals("", draft.expectedDeviceId)
        assertEquals("", draft.robotHost)
        assertNull(store.loadConfig())
    }

    @Test
    fun `explicit saved panel endpoint survives load without fallback rewrite`() {
        val store = ConfigStore(TestSharedPreferences())
        val saved = OnboardingDraft(panelBaseUrl = "https://panel.example.test")

        store.saveDraft(saved)

        assertEquals("https://panel.example.test", store.loadDraft().panelBaseUrl)
    }

    @Test
    fun `background access defer persists without marking current guidance version`() {
        val store = ConfigStore(TestSharedPreferences())
        store.markBackgroundAccessGuidanceShown(1)

        val deferredUntil = 123_456L
        store.deferBackgroundAccessGuidanceUntil(deferredUntil)

        assertEquals(1, store.backgroundAccessGuidanceVersion())
        assertEquals(deferredUntil, store.backgroundAccessGuidanceDeferredUntilMs())
    }

    @Test
    fun `background access defer clamps negative timestamps to zero`() {
        val store = ConfigStore(TestSharedPreferences())

        store.deferBackgroundAccessGuidanceUntil(-1L)

        assertEquals(0L, store.backgroundAccessGuidanceDeferredUntilMs())
    }
}
