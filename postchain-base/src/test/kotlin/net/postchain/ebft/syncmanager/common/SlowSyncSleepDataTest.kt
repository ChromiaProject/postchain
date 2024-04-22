package net.postchain.ebft.syncmanager.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlowSyncSleepDataTest {

    /**
     * We are at the ideal rate, no change in sleep really needed
     */
    @Test
    fun happyTest() {
        // Average will be 1.2 (108 / 90)
        val sssd = SlowSyncSleepData(SyncParameters(), 10, 90, 108, 100)
        val sleepResult = sssd.calculateSleep()
        assertEquals(100, sleepResult)
    }

    @Test
    fun tooSlowTest() {
        // Average is 1.7 (170 /97)
        val sssd = SlowSyncSleepData(SyncParameters(), 3, 97, 170, 100)
        val sleepResult = sssd.calculateSleep()
        assertEquals(78, sleepResult)
    }

    @Test
    fun wayTooSlowTest() {
        // Average is 2.0 (200/100)
        val sssd = SlowSyncSleepData(SyncParameters(), 0, 100, 200, 100)
        val sleepResult = sssd.calculateSleep()
        // Note: If we reduce to 50 we'll get oscillation.
        assertEquals(74, sleepResult)
    }

    @Test
    fun wayTooFastTest() {
        // Average is 1.02 (51/50
        val sssd = SlowSyncSleepData(SyncParameters(), 50, 50, 51, 100)
        val sleepResult = sssd.calculateSleep()
        // Note: If we increase to 200 we'll get oscillation.
        assertEquals(149, sleepResult)
    }
}