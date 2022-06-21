package net.postchain.ebft.syncmanager.common

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlowSyncSleepDataTest {


    /**
     * We are at the ideal rate, no change in sleep really needed
     */
    @Test
    fun happyTest() {
        // Average will be 1.2 (108 / 90)
        val sssd = SlowSyncSleepData(10, 90, 108, 100)
        val sleepResult = sssd.calculateSleep()
        assertTrue(sleepResult > 95)
        assertTrue(sleepResult < 105)
    }

    @Test
    fun tooSlowTest() {
        // Average is 1.7 (170 /97)
        val sssd = SlowSyncSleepData(3, 97, 170, 100)
        val sleepResult = sssd.calculateSleep()
        assertTrue(sleepResult < 90)
        assertTrue(sleepResult > 50)
    }

    @Test
    fun wayTooSlowTest() {
        // Average is 2.0 (200/100)
        val sssd = SlowSyncSleepData(0, 100, 200, 100)
        val sleepResult = sssd.calculateSleep()
        assertTrue(sleepResult < 80)
        assertTrue(sleepResult > 50) // Note: If we reduce to 50 we'll get oscillation.
    }

    @Test
    fun wayTooFastTest() {
        // Average is 1.02 (51/50
        val sssd = SlowSyncSleepData(50, 50, 51, 100)
        val sleepResult = sssd.calculateSleep()
        assertTrue(sleepResult > 130)
        assertTrue(sleepResult < 200) // Note: If we increase to 200 we'll get oscillation.
    }
}