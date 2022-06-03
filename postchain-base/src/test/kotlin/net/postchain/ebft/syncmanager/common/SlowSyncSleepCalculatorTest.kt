package net.postchain.ebft.syncmanager.common

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlowSyncSleepCalculatorTest {

    val sssc = SlowSyncSleepCalculator()

    /**
     * We are at the ideal rate, no change in sleep really needed
     */
    @Test
    fun happyTest() {
        val sleepResult = sssc.calculateSleep(10, 90, 1.2, 100)
        assertTrue(sleepResult > 95)
        assertTrue(sleepResult < 105)
    }

    @Test
    fun tooSlowTest() {
        val sleepResult = sssc.calculateSleep(3, 97, 1.7, 100)
        assertTrue(sleepResult < 90)
        assertTrue(sleepResult > 50)
    }

    @Test
    fun wayTooSlowTest() {
        val sleepResult = sssc.calculateSleep(0, 100, 2.0, 100)
        assertTrue(sleepResult < 60)
        assertTrue(sleepResult > 40)
    }

    @Test
    fun wayTooFastTest() {
        val sleepResult = sssc.calculateSleep(50, 50, 1.01, 100)
        assertTrue(sleepResult > 170)
        assertTrue(sleepResult < 230)
    }
}