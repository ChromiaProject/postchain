// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.*
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

/**
 * Minimal/raw version of the BC configuration.
 */
class BaseBlockchainConfigurationData(
        val data: GtvDictionary,
        partialContext: BlockchainContext,
        val blockSigMaker: SigMaker,
) {

    val context: BlockchainContext
    val subjectID = partialContext.nodeRID!!

    init {
        context = BaseBlockchainContext(
                partialContext.blockchainRID,
                resolveNodeID(partialContext.nodeID),
                partialContext.chainID,
                partialContext.nodeRID)
    }

    fun getSigners(): List<ByteArray> {
        return data[KEY_SIGNERS]!!.asArray().map { it.asByteArray() }
    }

    fun getBlockBuildingStrategy(): Gtv? {
        return data[KEY_BLOCKSTRATEGY]
    }

    internal fun strategy() = getBlockBuildingStrategy() // alias

    fun getBlockBuildingStrategyName(): String {
        return strategy()?.get(KEY_BLOCKSTRATEGY_NAME)?.asString() ?: ""
    }

    fun getMaxBlockSize(): Long {
        return strategy()?.get(KEY_BLOCKSTRATEGY_MAXBLOCKSIZE)?.asInteger()
                ?: (26 * 1024 * 1024) // default is 26 MiB
    }

    fun getMaxBlockTransactions(): Long {
        return strategy()?.get(KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS)?.asInteger() ?: 100
    }

    fun getMinInterBlockInterval(): Long {
        return strategy()?.get(KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL)?.asInteger() ?: 25
    }

    fun getMaxBlocktime(): Long {
        return getBlockBuildingStrategy()?.get(KEY_BLOCKSTRATEGY_MAXBLOCKTIME)?.asInteger() ?: 30000
    }

    fun getMaxTxDelay(): Long {
        return strategy()?.get(KEY_BLOCKSTRATEGY_MAXTXDELAY)?.asInteger() ?: 1000
    }

    /**
     * Note on POS-198: We actually do want the TX queue to fill up fast, b/c the client should display this
     * info to the user (spinning ball etc) so that the client understands that the system is down.
     * Alex spoke about making TX resend automatic, after a pause, when 503 error is returned, so that no action
     * from the user's side has to be taken to eventually get the TX into the queue.
     */
    fun getQueueCapacity(): Int {
        return strategy()?.get(KEY_BLOCKSTRATEGY_QUEUE_CAPACITY)?.asInteger()?.toInt()
                ?: 2500 // 5 seconds (if 500 tps)
    }

    fun getHistoricBRID(): BlockchainRid? {
        return data[KEY_HISTORIC_BRID]?.asByteArray()?.let { BlockchainRid(it) }
    }

    fun getDependenciesAsList(): List<BlockchainRelatedInfo> {
        return data[KEY_DEPENDENCIES]?.let { BaseDependencyFactory.build(it) } ?: listOf()
    }

    fun getMaxTransactionSize(): Long {
        return data[KEY_GTX]?.get(KEY_GTX_TX_SIZE)?.asInteger() ?: (25 * 1024 * 1024) // default is 25 MiB
    }

    fun getSyncInfrastructureName(): String? {
        return data[KEY_SYNC]?.asString()
    }

    fun getSyncInfrastructureExtensions(): List<String> {
        return data[KEY_SYNC_EXT]?.asArray()?.map { it.asString() } ?: listOf()
    }

    companion object {

        const val KEY_BLOCKSTRATEGY = "blockstrategy"
        const val KEY_BLOCKSTRATEGY_NAME = "name"
        const val KEY_BLOCKSTRATEGY_MAXBLOCKSIZE = "maxblocksize"
        const val KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS = "maxblocktransactions"
        const val KEY_BLOCKSTRATEGY_MININTERBLOCKINTERVAL = "mininterblockinterval"
        const val KEY_BLOCKSTRATEGY_MAXBLOCKTIME = "maxblocktime"
        const val KEY_BLOCKSTRATEGY_MAXTXDELAY = "maxtxdelay"
        const val KEY_BLOCKSTRATEGY_QUEUE_CAPACITY = "queuecapacity"

        const val KEY_CONFIGURATIONFACTORY = "configurationfactory"

        const val KEY_SIGNERS = "signers"

        const val KEY_GTX = "gtx"
        const val KEY_GTX_MODULES = "modules"
        const val KEY_GTX_TX_SIZE = "max_transaction_size"

        const val KEY_DEPENDENCIES = "dependencies"

        const val KEY_HISTORIC_BRID = "historic_brid"

        const val KEY_SYNC = "sync"
        const val KEY_SYNC_EXT = "sync_ext"

        /**
         * Factory method
         */
        fun build(
                rawConfigurationData: ByteArray,
                eContext: EContext,
                nodeId: Int,
                chainId: Long,
                subjectID: ByteArray,
                blockSigMaker: SigMaker,
        ): BaseBlockchainConfigurationData {
            val gtvData = GtvFactory.decodeGtv(rawConfigurationData)
            val brid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
            val context = BaseBlockchainContext(brid, nodeId, chainId, subjectID)
            return BaseBlockchainConfigurationData(gtvData as GtvDictionary, context, blockSigMaker)
        }
    }

    private fun resolveNodeID(nodeID: Int): Int {
        return if (nodeID == NODE_ID_AUTO) {
            if (subjectID == null) {
                NODE_ID_READ_ONLY
            } else {
                getSigners()
                        .indexOfFirst { it.contentEquals(subjectID) }
                        .let { i -> if (i == -1) NODE_ID_READ_ONLY else i }
            }
        } else {
            nodeID
        }
    }
}
