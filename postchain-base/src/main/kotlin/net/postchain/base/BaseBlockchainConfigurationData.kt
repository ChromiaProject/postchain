// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory

const val TRANSACTION_QUEUE_CAPACITY = 2500 // 5 seconds (if 500 tps)
/**
 * Minimal/raw version of the BC configuration.
 */
class BaseBlockchainConfigurationData(
    val data: GtvDictionary,
    partialContext: BlockchainContext,
    val blockSigMaker: SigMaker,
    val configurationComponentMap: MutableMap<String, Any> = HashMap() // For unusual settings, customizations etc.
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

    fun getBlockBuildingStrategyName(): String {
        val stratDict = data[KEY_BLOCKSTRATEGY]
        return stratDict?.get(KEY_BLOCKSTRATEGY_NAME)?.asString() ?: ""
    }

    fun getHistoricBRID(): BlockchainRid? {
        val bytes = data[KEY_HISTORIC_BRID]?.asByteArray()
        return if (bytes != null)
            BlockchainRid(bytes)
        else
            null
    }

    fun getBlockBuildingStrategy(): Gtv? {
        return data[KEY_BLOCKSTRATEGY]
    }

    // default is 26 MiB
    fun getMaxBlockSize(): Long {
        val stratDict = data[KEY_BLOCKSTRATEGY]
        return stratDict?.get(KEY_BLOCKSTRATEGY_MAXBLOCKSIZE)?.asInteger() ?: 26 * 1024 * 1024
    }

    fun getMaxBlockTransactions(): Long {
        val stratDict = data[KEY_BLOCKSTRATEGY]
        return stratDict?.get(KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS)?.asInteger() ?: 100
    }

    /**
     * Note on POS-198: We actually do want the TX queue to fill up fast, b/c the client should display this
     * info to the user (spinning ball etc) so that the client understands that the system is down.
     * Alex spoke about making TX resend automatic, after a pause, when 503 error is returned, so that no action
     * from the user's side has to be taken to eventually get the TX into the queue.
     */
    fun getQueueCapacity(): Int {
        val stratDict = data[KEY_BLOCKSTRATEGY]
        return stratDict?.get(KEY_BLOCKSTRATEGY_QUEUE_CAPACITY)?.asInteger()?.toInt() ?: TRANSACTION_QUEUE_CAPACITY
    }

    fun getDependenciesAsList(): List<BlockchainRelatedInfo> {
        val dep = data[KEY_DEPENDENCIES]
        return if (dep != null) {
            BaseDependencyFactory.build(dep!!)
        } else {
            // It is allowed to have no dependencies
            listOf<BlockchainRelatedInfo>()
        }
    }

    // default is 25 MiB
    fun getMaxTransactionSize(): Long {
        val gtxDict = data[KEY_GTX]
        return gtxDict?.get(KEY_GTX_TX_SIZE)?.asInteger() ?: 25 * 1024 * 1024
    }

    fun getSyncInfrastructureName(): String? {
        return data[KEY_SYNC]?.asString()
    }

    fun getSyncInfrastructureExtensions(): List<String> {
        val e = data[KEY_SYNC_EXT]
        return if (e != null) {
            e.asArray().map { it.asString() }
        } else {
            listOf()
        }
    }

    // Most chains don't have this setting
    fun getIcmfListener(): String? {
        return data[KEY_ICMF_LISTENER]?.asString()
    }

    fun getComponentMap() = configurationComponentMap

    /**
     * We can add anything in here, should be used for unusual configurations.
     *
     * @param name is what the component is called
     * @param obj is really any type of component. Must be cast back to "real" type after it has been fetched.
     */
    fun addComponentToMap(name: String, obj: Any) {
        if (configurationComponentMap.containsKey(name)) {
            throw ProgrammerMistake("Adding component with existing key: $name")
        } else {
            this.configurationComponentMap[name] = obj
        }
    }

    companion object {

        const val KEY_BLOCKSTRATEGY = "blockstrategy"
        const val KEY_BLOCKSTRATEGY_NAME = "name"
        const val KEY_BLOCKSTRATEGY_MAXBLOCKSIZE = "maxblocksize"
        const val KEY_BLOCKSTRATEGY_MAXBLOCKTRANSACTIONS = "maxblocktransactions"
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

        const val KEY_ICMF_SOURCE = "icmfsource" // Only use this setting for manual mode (only)
        const val KEY_ICMF_LISTENER = "icmflistener" // Only use this setting for managed mode


        /**
         * Factory method
         */
        fun build(rawConfigurationData: ByteArray,
                  eContext: EContext,
                  nodeId: Int,
                  chainId: Long,
                  subjectID: ByteArray,
                  blockSigMaker: SigMaker,
                  configurationComponentMap: MutableMap<String, Any> = HashMap()
        ): BaseBlockchainConfigurationData {
            val gtvData = GtvFactory.decodeGtv(rawConfigurationData)
            val brid = DatabaseAccess.of(eContext).getBlockchainRid(eContext)!!
            val context = BaseBlockchainContext(brid, nodeId, chainId, subjectID)
            return BaseBlockchainConfigurationData(gtvData as GtvDictionary, context, blockSigMaker, configurationComponentMap)
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
