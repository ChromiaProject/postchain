package net.postchain.ebft.syncmanager.common

import net.postchain.common.config.Config

import java.util.concurrent.TimeUnit

data class SyncPeerParameters(
        var blacklistingTimeoutMs: Long = TimeUnit.MINUTES.toMillis(10),

        /** How long we keep each error */
        var blacklistingErrorTimeoutMs: Long = TimeUnit.HOURS.toMillis(1),

        /** Threshold for how many errors we can receive before blacklisting */
        var maxErrorsBeforeBlacklisting: Int = 10,

        val disconnectTimeout: Long = 10000,

        /** Once drained, how long until we resurrect */
        var resurrectDrainedTime: Long = 10000,

        /** Once unresponsive, how long until we resurrect */
        var resurrectUnresponsiveTime: Long = 20000,
) : Config