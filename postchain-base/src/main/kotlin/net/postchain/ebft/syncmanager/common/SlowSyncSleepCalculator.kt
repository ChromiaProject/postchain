package net.postchain.ebft.syncmanager.common

class SlowSyncSleepCalculator {

    val dampingFactor = 0.5

    fun calculateSleep(
        numberOfNoBlockReplies: Int, // Number of empty replies since last calibration
        numberOfHaveBlockReplies: Int, // Number of non-empty replies since last calibration
        averageBlocksFound: Double, // Average number of blocks in non-empty replies
        currentSleepMs: Long
    ): Long {
        val totalReplies = numberOfNoBlockReplies + numberOfHaveBlockReplies
        val failureRate: Double = (numberOfNoBlockReplies * 1.0) / totalReplies // Ideally should be 0.1

        val multiplier = if (failureRate > 0.31 && averageBlocksFound < 1.31) {
            // We should slow down
            1.0 + failureRate // This will be a rather slow sleep increase, but it prevents oscillations
        } else if (averageBlocksFound > 1.31 && failureRate < 0.31) {
            // We should speed up
            1 - (dampingFactor * (1.0 / averageBlocksFound)) // The "damping factor" prevents oscillations
        } else {
            1.0 // Good enough, Do nothing
        }

        return (currentSleepMs * multiplier).toLong()
    }
}