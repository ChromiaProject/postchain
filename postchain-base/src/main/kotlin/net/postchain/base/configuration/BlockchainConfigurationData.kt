package net.postchain.base.configuration

import net.postchain.base.BaseDependencyFactory
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.*

data class BlockchainConfigurationData(
        @RawGtv
        val rawConfig: Gtv,

        @Name(KEY_SIGNERS)
        val signers: List<ByteArray>,
        @Name(KEY_SYNC)
        @Nullable
        val synchronizationInfrastructure: String?,
        @Name(KEY_SYNC_EXT)
        @Nullable
        val synchronizationInfrastructureExtension: List<String>?,
        @Name(KEY_CONFIGURATIONFACTORY)
        val configurationFactory: String,
        /**
         * NB: The default value is set so that the TX queue will fill up fast, b/c the client should display this
         * info to the user (spinning ball etc) so that the client understands that the system is down.
         * Alex spoke about making TX resend automatic, after a pause, when 503 error is returned, so that no action
         * from the user's side has to be taken to eventually get the TX into the queue.
         */
        @Name(KEY_QUEUE_CAPACITY)
        @DefaultValue(defaultLong = 2500) // 5 seconds (if 500 tps)
        val txQueueSize: Long,

        @Name(KEY_BLOCKSTRATEGY_NAME)
        @Nested(KEY_BLOCKSTRATEGY)
        @DefaultValue(defaultString = "net.postchain.base.BaseBlockBuildingStrategy")
        val blockStrategyName: String,
        @Name(KEY_BLOCKSTRATEGY)
        @Nullable
        val blockStrategy: Gtv?,
        @Name(KEY_HISTORIC_BRID)
        @Nullable
        private val historicBridAsByteArray: ByteArray?,
        @Name(KEY_DEPENDENCIES)
        @Nullable
        private val blockchainDependenciesRaw: Gtv?,
        @Name(KEY_GTX)
        @Nullable
        val gtx: Gtv?,
        @Name(KEY_MAX_TX_EXECUTION_TIME)
        @DefaultValue(defaultLong = 0)
        val maxTxExecutionTime: Long,
        @Name(KEY_REVOLT)
        @Nullable
        val revoltConfigData: Gtv?,
        @Name(KEY_CONFIG_CONSENSUS_STRATEGY)
        @Nullable
        private val configConsensusStrategyString: String?
) {
    val historicBrid = historicBridAsByteArray?.let { BlockchainRid(it) }
    val blockchainDependencies = blockchainDependenciesRaw?.let { BaseDependencyFactory.build(it) } ?: listOf()
    val configConsensusStrategy = configConsensusStrategyString?.let { ConfigConsensusStrategy.valueOf(it) }

    companion object {
        @JvmStatic
        fun fromRaw(
                rawConfigurationData: ByteArray): BlockchainConfigurationData =
                GtvFactory.decodeGtv(rawConfigurationData).toObject()
    }
}
