package net.postchain.base

import net.postchain.common.BlockchainRid
import net.postchain.core.NodeRid
import net.postchain.ebft.worker.WorkerContext

class HistoricBlockchainContext(
        val historicBrid: BlockchainRid,
        val ancestors: Map<BlockchainRid, Set<NodeRid>>
) {

    lateinit var contextCreator: (BlockchainRid, HistoricBlockchainContext) -> WorkerContext

    fun getHistoricWorkerContext(blockchainRid: BlockchainRid) = contextCreator(blockchainRid, this)

    /**
     * We want to use all ancestors we have when we look for blocks
     * Aliases are "sneaky links" used to pretend that some other blockchain is the one we need.
     *
     * @param myBRID is a BC RID we always wanna use, i.e. the "real" one
     * @return a list of alternative names
     */
    fun getChainsToSyncFrom(myBRID: BlockchainRid): List<BlockchainRid> = buildList {
        add(myBRID)
        add(historicBrid)
        addAll(ancestors.keys)
    }
}