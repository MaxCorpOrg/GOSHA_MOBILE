package com.maxcorp.gosha.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuRecoveryRetryPolicyTest {
    @Test
    fun `active recovery on home wifi gets bounded retries`() {
        val first = MenuRecoveryRetryPolicy.next(
            completedAttempts = 0,
            recoveryNeeded = true,
            hasHomeWifi = true,
        )
        val third = MenuRecoveryRetryPolicy.next(
            completedAttempts = 2,
            recoveryNeeded = true,
            hasHomeWifi = true,
        )

        requireNotNull(first)
        requireNotNull(third)
        assertEquals(1, first.attempt)
        assertEquals(3_000L, first.delayMs)
        assertEquals(3, third.attempt)
        assertEquals(9_000L, third.delayMs)
    }

    @Test
    fun `retry limit cannot schedule a fourth attempt`() {
        assertNull(
            MenuRecoveryRetryPolicy.next(
                completedAttempts = MenuRecoveryRetryPolicy.LIMIT,
                recoveryNeeded = true,
                hasHomeWifi = true,
            )
        )
        assertEquals(
            true,
            MenuRecoveryRetryPolicy.exhausted(
                completedAttempts = MenuRecoveryRetryPolicy.LIMIT,
                recoveryNeeded = true,
                hasHomeWifi = true,
            )
        )
    }

    @Test
    fun `retry is disabled without recovery need or home wifi`() {
        assertNull(
            MenuRecoveryRetryPolicy.next(
                completedAttempts = 0,
                recoveryNeeded = false,
                hasHomeWifi = true,
            )
        )
        assertNull(
            MenuRecoveryRetryPolicy.next(
                completedAttempts = 0,
                recoveryNeeded = true,
                hasHomeWifi = false,
            )
        )
        assertEquals(
            false,
            MenuRecoveryRetryPolicy.exhausted(
                completedAttempts = MenuRecoveryRetryPolicy.LIMIT,
                recoveryNeeded = false,
                hasHomeWifi = true,
            )
        )
    }
}
