package com.lowvolume.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitsTest {

    @Test
    fun allowedIndexRoundsDown() {
        assertEquals(7, VolumeLimiter.allowedIndex(max = 15, min = 0, percent = 50))
        assertEquals(15, VolumeLimiter.allowedIndex(max = 15, min = 0, percent = 100))
        assertEquals(0, VolumeLimiter.allowedIndex(max = 15, min = 0, percent = 0))
    }

    @Test
    fun allowedIndexRespectsStreamMinimum() {
        assertEquals(1, VolumeLimiter.allowedIndex(max = 5, min = 1, percent = 0))
    }

    @Test
    fun allowedIndexClampsPercent() {
        assertEquals(15, VolumeLimiter.allowedIndex(max = 15, min = 0, percent = 150))
        assertEquals(0, VolumeLimiter.allowedIndex(max = 15, min = 0, percent = -10))
    }

    @Test
    fun lockoutGrowsAndIsCapped() {
        assertEquals(30_000L, PasswordManager.lockoutDuration(5))
        assertEquals(60_000L, PasswordManager.lockoutDuration(6))
        assertEquals(PasswordManager.MAX_LOCKOUT_MS, PasswordManager.lockoutDuration(50))
    }
}
