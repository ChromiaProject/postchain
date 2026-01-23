package net.postchain.base.configuration

import net.postchain.base.BaseDependencyFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.DefaultEmpty
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nested
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtv.merkleHash

data class BlockchainConfigurationData(
        @param:RawGtv
        val rawConfig: Gtv,

        @param:Name(KEY_SIGNERS)
        val signers: List<ByteArray>,
        @param:Name(KEY_SYNC)
        @param:Nullable
        val synchronizationInfrastructure: String?,
        @param:Name(KEY_SYNC_EXT)
        @param:Nullable
        val synchronizationInfrastructureExtension: List<String>?,
        @param:Name(KEY_CONFIGURATIONFACTORY)
        val configurationFactory: String,
        /**
         * NB: The default value is set so that the TX queue will fill up fast, b/c the client should display this
         * info to the user (spinning ball etc) so that the client understands that the system is down.
         * Alex spoke about making TX resend automatic, after a pause, when 503 error is returned, so that no action
         * from the user's side has to be taken to eventually get the TX into the queue.
         */
        @param:Name(KEY_QUEUE_CAPACITY)
        @param:DefaultValue(defaultLong = 2500) // 5 seconds (if 500 tps)
        val txQueueSize: Long,
        @param:Name(KEY_QUEUE_TX_RECHECK_INTERVAL)
        @param:DefaultValue(defaultLong = 5 * 60 * 1000) // 5 minutes
        val txQueueRecheckInterval: Long,

        @param:Name(KEY_BLOCKSTRATEGY_NAME)
        @param:Nested(KEY_BLOCKSTRATEGY)
        @param:DefaultValue(defaultString = "net.postchain.base.BaseBlockBuildingStrategy")
        val blockStrategyName: String,
        @param:Name(KEY_BLOCKSTRATEGY)
        @param:Nullable
        val blockStrategy: Gtv?,
        @param:Name(KEY_HISTORIC_BRID)
        @param:Nullable
        private val historicBridAsByteArray: ByteArray?,
        @param:Name(KEY_DEPENDENCIES)
        @param:Nullable
        private val blockchainDependenciesRaw: Gtv?,
        @param:Name(KEY_GTX)
        @param:Nullable
        val gtx: Gtv?,
        @param:Name(KEY_CONFIG_CONSENSUS_STRATEGY)
        @param:Nullable
        private val configConsensusStrategyString: String?,
        @param:Name(KEY_MAX_BLOCK_FUTURE_TIME)
        @param:DefaultValue(defaultLong = 60 * 1000) // 1 minute
        val maxBlockFutureTime: Long,
        @param:Name(KEY_ADD_PRIMARY_KEY_TO_HEADER)
        @param:DefaultValue(defaultBoolean = false)
        val addPrimaryKeyToHeader: Boolean,
        @param:Name(KEY_FEATURES)
        @param:DefaultEmpty
        val features: Map<String, Gtv>,
        @param:Name(KEY_SNAPSHOT)
        @param:Nullable
        val snapshot: Gtv?,
        @param:Name(KEY_QUERY_TIMEOUT_SECONDS)
        @param:DefaultValue(defaultLong = 60)
        val queryTimeoutSeconds: Long,
) {
    val historicBrid = historicBridAsByteArray?.let { BlockchainRid(it) }
    val blockchainDependencies = blockchainDependenciesRaw?.let { BaseDependencyFactory.build(it) } ?: listOf()
    val configConsensusStrategy = configConsensusStrategyString?.let { ConfigConsensusStrategy.valueOf(it) }
    val merkleHashVersion: Long = features[BlockchainFeatures.merkle_hash_version.name]?.asInteger() ?: 1L
    val merkleHashCalculator by lazy {
        makeMerkleHashCalculator(merkleHashVersion)
    }
    val configHash by lazy {
        rawConfig.merkleHash(merkleHashCalculator)
    }
    val snapshotsEnabled: Boolean = merkleHashVersion >= 2 && features[BlockchainFeatures.snapshot_enabled.name]?.asBoolean() ?: false

    companion object {
        @JvmStatic
        fun fromRaw(
                rawConfigurationData: ByteArray): BlockchainConfigurationData =
                GtvFactory.decodeGtv(rawConfigurationData).toObject()

        @JvmStatic
        fun merkleHash(configuration: Gtv): Hash {
            return configuration.merkleHash(merkleHashCalculator(configuration))
        }

        @JvmStatic
        fun merkleHashCalculator(configuration: Gtv): GtvMerkleHashCalculatorBase {
            return makeMerkleHashCalculator(merkleHashVersion(configuration))
        }

        @JvmStatic
        fun merkleHashVersion(configuration: Gtv): Long {
            val features = configuration[KEY_FEATURES]?.asDict()
            return features?.get(BlockchainFeatures.merkle_hash_version.name)?.asInteger() ?: 1L
        }

        @JvmStatic
        fun snapshotSyncEnabled(configuration: Gtv): Boolean {
            val features = configuration[KEY_FEATURES]?.asDict()
            return features?.get(BlockchainFeatures.snapshot_enabled.name)?.asBoolean() ?: false
        }
    }
}
