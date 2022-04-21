package net.postchain.base

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.*
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.*

data class BlockchainConfigurationData(
        @RawGtv
        val rawConfig: Gtv,
        @Transient("sigmaker")
        val blockSigMaker: SigMaker,
        @Transient("partialContext")
        private val partialContext: BlockchainContext,

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

        @Name(KEY_QUEUE_CAPACITY)
        @DefaultValue(defaultLong =  2500) // 5 seconds (if 500 tps)
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
        val gtx: Gtv?
) {
    val historicBrid = historicBridAsByteArray?.let { BlockchainRid(it) }
    val blockchainDependencies = blockchainDependenciesRaw?.let { BaseDependencyFactory.build(it) } ?: listOf()


    val context = BaseBlockchainContext(
            partialContext.blockchainRID,
            resolveNodeID(partialContext.nodeID, partialContext.nodeRID!!),
            partialContext.chainID,
            partialContext.nodeRID
    )

    private fun resolveNodeID(nodeID: Int, subjectID: ByteArray): Int {
        return if (nodeID == NODE_ID_AUTO) {
            signers.indexOfFirst { it.contentEquals(subjectID) }
                    .let { i -> if (i == -1) NODE_ID_READ_ONLY else i }
        } else {
            nodeID
        }
    }

    companion object {
        @JvmStatic
        fun fromRaw(
                rawConfigurationData: ByteArray,
                eContext: EContext,
                nodeId: Int,
                chainId: Long,
                subjectID: ByteArray,
                blockSigMaker: SigMaker,
        ): BlockchainConfigurationData {
            val gtvData = GtvFactory.decodeGtv(rawConfigurationData)
            val brid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
            val context = BaseBlockchainContext(brid, nodeId, chainId, subjectID)
            return gtvData.toObject(mapOf("sigmaker" to blockSigMaker,
                    "partialContext" to context))
        }
    }
}
