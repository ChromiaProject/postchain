package net.postchain.ebft.syncmanager.common

object SlowSyncSleepConst {
    const val DAMPING_FACTOR = 0.5
    const val START_SLEEP_MS = 500L // This is the slowest block building time for Postchain, good starting point
    const val REPLIES_BEFORE_CALIBRATION = 20 // Should be enough data to recalibrate
    const val TOO_MANY_BLOCKS_LIMIT = 1.31 // Ok to get two blocks 1/3 of the times
    const val TOO_MANY_FAILURES_LIMIT = 0.31 // Ok to not find anything 1/3 of the times
}