package net.postchain.base.config

import net.postchain.base.*
import net.postchain.base.config.BlockStrategyKeys.*
import net.postchain.base.config.BlockchainConfigKeys.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.toBoolean

const val TRANSACTION_QUEUE_CAPACITY = 2500 // 5 seconds (if 500 tps)
data class GtxConfig(
        val maxTxSize: Long,
        val modules: List<String>,
        val dontAllowOverrides: Boolean
)

data class BlockBuildingStrategyConfig(
        val maxBlockSize: Long,
        val maxBlockTransactions: Long,
        val maxBlockTime: Long,
        val maxTxDelay: Long,
        val minInterBlockInterval: Long,
        val queueCapacity: Int
)

data class BlockchainConfig(
        val raw: Gtv,
        val synchronizationInfrastructure: String?,
        val synchronizationInfrastructureExtensions: List<String>,
        val blockchainConfigFactory: String,
        val signers: List<ByteArray>,
        val blockBuildingStrategy: String,
        val historicBrid: BlockchainRid?,
        val blockchainDependencies: List<BlockchainRelatedInfo>,
        val blockBuildingStrategyConfig: BlockBuildingStrategyConfig,
        val gtxConfig: GtxConfig,
        val blockSigMaker: SigMaker,
        val blockchainContext: BlockchainContext
) {
    companion object {
        private fun resolveNodeID(nodeID: Int, signers: List<ByteArray>, subjectID: ByteArray?): Int {
            return if (nodeID == NODE_ID_AUTO) {
                if (subjectID == null) {
                    NODE_ID_READ_ONLY
                } else {
                    signers.indexOfFirst { it.contentEquals(subjectID) }
                            .let { i -> if (i == -1) NODE_ID_READ_ONLY else i }
                }
            } else {
                nodeID
            }
        }

        @JvmStatic
        fun fromRaw(rawConfigData: ByteArray,
                    blockSigMaker: SigMaker,
                    eContext: EContext,
                    nodeId: Int,
                    chainId: Long,
                    subjectId: ByteArray

        ): BlockchainConfig {
            val configGtv = GtvFactory.decodeGtv(rawConfigData) as GtvDictionary
            val brid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
            return fromGtv(configGtv, blockSigMaker, brid, nodeId, chainId, subjectId)
        }
        @JvmStatic
        fun fromGtv(configGtv: GtvDictionary, blockSigMaker: SigMaker, brid: BlockchainRid, nodeId: Int, chainId: Long, subjectId: ByteArray): BlockchainConfig {
            val signers = (Signers from configGtv)!!.asArray().map { it.asByteArray() }

            return BlockchainConfig(
                    raw = configGtv,
                    synchronizationInfrastructure = (SyncInfra from configGtv)
                            ?.asString(),
                    synchronizationInfrastructureExtensions = (SyncInfraExt from configGtv)
                            ?.asArray()?.map { it.asString() } ?: listOf(),
                    blockchainConfigFactory = (ConfigurationFactory from configGtv)
                            !!.asString(),
                    signers = signers,
                    blockBuildingStrategy = (BlockStrategyClass from configGtv)
                            ?.asString() ?: "",
                    historicBrid = (HistoricBrid from configGtv)
                            ?.asByteArray()?.let { BlockchainRid(it) },
                    blockchainDependencies = (ChainDependencies from configGtv)
                            ?.let { BaseDependencyFactory.build(it) } ?: listOf(),
                    blockBuildingStrategyConfig = BlockBuildingStrategyConfig(
                            maxBlockSize = (MaxBlockSize from configGtv)
                                    ?.asInteger() ?: (26 * 1024 * 1024),
                            maxBlockTransactions = (MaxBlockTransactions from configGtv)
                                    ?.asInteger() ?: 100,
                            queueCapacity = (QueueCapacity from configGtv)?.asInteger()
                                    ?.toInt() ?: TRANSACTION_QUEUE_CAPACITY,
                            maxBlockTime = (MaxBlockTime from configGtv)?.asInteger() ?: 3000,
                            maxTxDelay = (MaxTxDelay from configGtv)?.asInteger() ?: 1000,
                            minInterBlockInterval = (MinInterBlockInterval from configGtv)?.asInteger() ?: 25
                    ),
                    gtxConfig = GtxConfig(
                            maxTxSize = (GtxConfigKeys.GtxMaxTxSize from configGtv)
                                    ?.asInteger() ?: (25 * 1024 * 1024),
                            modules = (GtxConfigKeys.GtxModules from configGtv)
                                    ?.asArray()?.map { it.asString() } ?: listOf(),
                            dontAllowOverrides = (GtxConfigKeys.GtxDontAllowOverrides from configGtv)
                                    ?.asInteger()?.toBoolean() ?: false
                    ),
                    blockSigMaker,
                    BaseBlockchainContext(
                            brid, resolveNodeID(nodeId, signers, subjectId), chainId, subjectId
                    )
            )
        }
    }
}


