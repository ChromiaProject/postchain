package net.postchain.ebft.syncmanager.common

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

/**
 * Tuning parameters for various sychronizers (used to load blocks from other nodes).
 *
 * Note: All times are in ms.
 */
data class SyncParameters(
    var resurrectDrainedTime: Long = 10000,
    var resurrectUnresponsiveTime: Long = 20000,
    /**
     * For tiny blocks it might make sense to increase parallelism to, eg 100,
     * to increase throughput by ~6x (as experienced through experiments),
     * but for non-trivial blockchains, this will require substantial amounts
     * of memory, worst case about parallelism*blocksize.
     *
     * There seems to be a sweet-spot throughput-wise at parallelism=120,
     * but it can come at great memory cost. We set this to 10
     * to be safe.
     *
     * Ultimately, this should be a configuration setting.
     */
    var parallelism: Int = 10,
    /**
     * Don't exit fastsync for at least this amount of time (ms).
     * This gives the connection manager some time to accumulate
     * connections so that the random peer selection has more
     * peers to chose from, to avoid exiting fastsync
     * prematurely because one peer is connected quicker, giving
     * us the impression that there is only one reachable node.
     *
     * Example: I'm A(height=-1), and B(-1),C(-1),D(0) are peers. When entering FastSync
     * we're only connected to B.
     *
     * * Send a GetBlockHeaderAndBlock(0) to B
     * * B replies with empty block header and we mark it as drained(-1).
     * * We conclude that we have drained all peers at -1 and exit fastsync
     * * C and D connections are established.
     *
     * We have exited fastsync before we had a chance to sync from C and D
     *
     * Sane values:
     * Replicas: not used
     * Signers: 60000ms
     * Tests with single node: 0
     * Tests with multiple nodes: 1000
     */
    var exitDelay: Long = 60000,
    var pollPeersInterval: Long = 10000,
    var jobTimeout: Long = 10000,
    var loopInterval: Long = 100,
    var mustSyncUntilHeight: Long = -1,
    var maxErrorsBeforeBlacklisting: Int = 10,
    val disconnectTimeout: Long = 10000,
    val slowSyncEnabled: Boolean = true,
    /**
     * 10 minutes in milliseconds
     */
    var blacklistingTimeoutMs: Long = 10 * 60 * 1000) : Config {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig, init: (SyncParameters) -> Unit = {}): SyncParameters {
            return SyncParameters(
                    exitDelay = config.getEnvOrLong("POSTCHAIN_FASTSYNC_EXIT_DELAY", "fastsync.exit_delay", 60000),
                    jobTimeout = config.getEnvOrLong("POSTCHAIN_FASTSYNC_JOB_TIMEOUT", "fastsync.job_timeout", 10000),
                    disconnectTimeout = config.getEnvOrLong("POSTCHAIN_FASTSYNC_DISCONNECT_TIMEOUT", "fastsync.disconnect_timeout", 10000),
                    slowSyncEnabled = config.getEnvOrBoolean("POSTCHAIN_SLOWSYNC_ENABLED", "slowsync.enabled", true)
            ).also(init)
        }
    }
}