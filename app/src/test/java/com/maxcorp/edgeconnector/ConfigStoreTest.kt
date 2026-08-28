package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigStoreTest {
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
