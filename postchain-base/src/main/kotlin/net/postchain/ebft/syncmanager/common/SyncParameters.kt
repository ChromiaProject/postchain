package net.postchain.ebft.syncmanager.common

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig
import java.util.concurrent.TimeUnit

/**
 * Tuning parameters for various sychronizers (used to load blocks from other nodes).
 *
 * Note: All times are in ms.
 */
data class SyncParameters(
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
        var jobTimeout: Long = 10000,
        var loopInterval: Long = 100,
        var mustSyncUntilHeight: Long = -1,
        var syncToExactHeight: Long = -1,

        val slowSyncEnabled: Boolean = true,
        var slowSyncMaxSleepTime: Long = TimeUnit.MINUTES.toMillis(1),
        var slowSyncMinSleepTime: Long = 20,
        /**
         * A node must answer before this, or we'll give up
         */
        var slowSyncMaxPeerWaitTime: Long = 2000,
        /**
         * The minimum number of blocks to be behind to use snapshot syncing
         */
        var snapshotSyncThreshold: Long = 100_000,
        /**
         * The size limit of a snapshot sync data message, in bytes. A [net.postchain.ebft.message.SnapshotData] message is filled with snapshot
         * data until this limit is reached.
         */
        var snapshotSyncMaxDataSize: Long = BlockPacker.MAX_PACKAGE_CONTENT_BYTES.toLong(),
        /**
         * The time limit to spend on populating a snapshot sync data message, in ms. A [net.postchain.ebft.message.SnapshotData] message is filled with snapshot
         * data until this limit is reached.
         */
        var snapshotSyncMaxLoadTime: Long = 5_000,
        var syncPeerParameters: SyncPeerParameters = SyncPeerParameters(),
        var snapshotSyncPeerParameters: SyncPeerParameters = SyncPeerParameters(),
) : Config {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig, init: (SyncParameters) -> Unit = {}): SyncParameters {
            val syncPeerParameters = SyncPeerParameters(
                    resurrectDrainedTime = config.getEnvOrLong("POSTCHAIN_FASTSYNC_RESURRECT_DRAINED_TIME", "fastsync.resurrect_drained_time", 10000),
                    resurrectUnresponsiveTime = config.getEnvOrLong("POSTCHAIN_FASTSYNC_RESURRECT_UNRESPONSIVE_TIME", "fastsync.resurrect_unresponsive_time", 20000),
                    disconnectTimeout = config.getEnvOrLong("POSTCHAIN_FASTSYNC_DISCONNECT_TIMEOUT", "fastsync.disconnect_timeout", 10000),
                    maxErrorsBeforeBlacklisting = config.getEnvOrInt("POSTCHAIN_FASTSYNC_MAX_ERRORS_BEFORE_BLACKLISTING", "fastsync.max_errors_before_blacklisting", 10),
                    blacklistingTimeoutMs = config.getEnvOrLong("POSTCHAIN_FASTSYNC_BLACKLISTING_TIMEOUT", "fastsync.blacklisting_timeout", TimeUnit.MINUTES.toMillis(10)),
                    blacklistingErrorTimeoutMs = config.getEnvOrLong("POSTCHAIN_FASTSYNC_BLACKLISTING_ERROR_TIMEOUT", "fastsync.blacklisting_error_timeout", TimeUnit.HOURS.toMillis(1)),
            )
            return SyncParameters(
                    parallelism = config.getEnvOrInt("POSTCHAIN_FASTSYNC_PARALLELISM", "fastsync.parallelism", 10),
                    exitDelay = config.getEnvOrLong("POSTCHAIN_FASTSYNC_EXIT_DELAY", "fastsync.exit_delay", 60000),
                    jobTimeout = config.getEnvOrLong("POSTCHAIN_FASTSYNC_JOB_TIMEOUT", "fastsync.job_timeout", 10000),
                    loopInterval = config.getEnvOrLong("POSTCHAIN_FASTSYNC_LOOP_INTERVAL", "fastsync.loop_interval", 100),
                    mustSyncUntilHeight = config.getEnvOrLong("POSTCHAIN_FASTSYNC_MUST_SYNC_UNTIL_HEIGHT", "fastsync.must_sync_until_height", -1),
                    syncToExactHeight = config.getEnvOrLong("POSTCHAIN_FASTSYNC_SYNC_TO_EXACT_HEIGHT", "fastsync.sync_to_exact_height", -1),
                    slowSyncEnabled = config.getEnvOrBoolean("POSTCHAIN_SLOWSYNC_ENABLED", "slowsync.enabled", true),
                    slowSyncMaxSleepTime = config.getEnvOrLong("POSTCHAIN_SLOWSYNC_MAX_SLEEP_TIME", "slowsync.max_sleep_time", TimeUnit.MINUTES.toMillis(1)),
                    slowSyncMinSleepTime = config.getEnvOrLong("POSTCHAIN_SLOWSYNC_MIN_SLEEP_TIME", "slowsync.min_sleep_time", 20),
                    slowSyncMaxPeerWaitTime = config.getEnvOrLong("POSTCHAIN_SLOWSYNC_MAX_PEER_WAIT_TIME", "slowsync.max_peer_wait_time", 2000),
                    // TODO: document all snapshot configs
                    snapshotSyncThreshold = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_THRESHOLD", "snapshotsync.threshold", 100_000),
                    snapshotSyncMaxDataSize = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_MAX_DATA_SIZE", "snapshotsync.max_data_size", BlockPacker.MAX_PACKAGE_CONTENT_BYTES.toLong()),
                    snapshotSyncMaxLoadTime = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_MAX_TIME", "snapshotsync.max_load_time", 5_000L),
                    syncPeerParameters = syncPeerParameters,
                    snapshotSyncPeerParameters = SyncPeerParameters(
                            resurrectDrainedTime = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_RESURRECT_DRAINED_TIME", "snapshotsync.resurrect_drained_time", TimeUnit.MINUTES.toMillis(10)),
                            resurrectUnresponsiveTime = syncPeerParameters.resurrectUnresponsiveTime,
                            disconnectTimeout = syncPeerParameters.disconnectTimeout,
                            maxErrorsBeforeBlacklisting = config.getEnvOrInt("POSTCHAIN_SNAPSHOTSYNC_MAX_ERRORS_BEFORE_BLACKLISTING", "snapshotsync.max_errors_before_blacklisting", 2),
                            blacklistingTimeoutMs = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_BLACKLISTING_TIMEOUT", "snapshotsync.blacklisting_timeout", TimeUnit.MINUTES.toMillis(10)),
                            blacklistingErrorTimeoutMs = config.getEnvOrLong("POSTCHAIN_SNAPSHOTSYNC_BLACKLISTING_ERROR_TIMEOUT", "snapshotsync.blacklisting_error_timeout", TimeUnit.HOURS.toMillis(1)),
                    ),
            ).also(init)
        }
    }
}