package net.postchain.ebft.syncmanager.common

import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.DAMPING_FACTOR
import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.REPLIES_BEFORE_CALIBRATION
import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.START_SLEEP_MS
import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.TOO_MANY_BLOCKS_LIMIT
import net.postchain.ebft.syncmanager.common.SlowSyncSleepConst.TOO_MANY_FAILURES_LIMIT

/**
 * The idea behind the sleep calculation is that we will find the perfect sleep duration when we have enough data,
 * it is not meant to immediately converge to the perfect sleep, but in time.
 * Obviously this will only work if the load on the blockchain is even, so that blocks are produced regularly
 */
class SlowSyncSleepData(
        val params: SyncParameters,
        var numberOfNoBlockReplies: Int = 0, // Number of empty replies since last calibration
        var numberOfHaveBlockReplies: Int = 0, // Number of non-empty replies since last calibration
        var blocksProcessedSinceLastCalibration: Int = 0, // Number of block we've managed to save to DB.
        var currentSleepMs: Long = START_SLEEP_MS
) {

    private fun totalReplies() = numberOfHaveBlockReplies + numberOfNoBlockReplies

    private val smallNum = 0.000001

    fun updateData(blocksProcessed: Int) {
        if (totalReplies() > REPLIES_BEFORE_CALIBRATION) {
            // Time to calibrate sleep
            currentSleepMs = calculateSleep()

            // Reset the counters
            numberOfHaveBlockReplies = 0
            numberOfNoBlockReplies = 0
            blocksProcessedSinceLastCalibration = 0
        }

        if (blocksProcessed > 0) {
            numberOfHaveBlockReplies++
            blocksProcessedSinceLastCalibration += blocksProcessed
        } else {
            numberOfNoBlockReplies++
        }
    }

    fun calculateSleep(): Long {
        val safeHaveBlocksCount = numberOfHaveBlockReplies + smallNum
        val averageBlocksFound: Double = (blocksProcessedSinceLastCalibration * 1.0) / safeHaveBlocksCount
        val safeTotalReplies = totalReplies() + smallNum
        val failureRate: Double = (numberOfNoBlockReplies * 1.0) / safeTotalReplies // Ideally should be 0.1

        val multiplier = if (failureRate > TOO_MANY_FAILURES_LIMIT && averageBlocksFound < TOO_MANY_BLOCKS_LIMIT) {
            // We should slow down
            1.0 + failureRate // This will be a rather slow sleep increase, but it prevents oscillations
        } else if (averageBlocksFound > TOO_MANY_BLOCKS_LIMIT && failureRate < TOO_MANY_FAILURES_LIMIT) {
            // We should speed up
            val safeAverage = averageBlocksFound + smallNum
            1 - (DAMPING_FACTOR * (1.0 / safeAverage)) // The "damping factor" prevents oscillations
        } else {
            1.0 // Good enough, Do nothing
        }

        val sleep = (currentSleepMs * multiplier).toLong()

        return if (sleep > params.slowSyncMaxSleepTime) {
            params.slowSyncMaxSleepTime
        } else if (sleep < params.slowSyncMinSleepTime) {
            params.slowSyncMinSleepTime
        } else {
            sleep
        }
    }
}