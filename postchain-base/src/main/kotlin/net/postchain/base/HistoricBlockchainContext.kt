package net.postchain.base

import net.postchain.core.BlockQueries
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.x.XPeerID

class HistoricBlockchainContext(val historicBrid: BlockchainRid,
                                val aliases: Map<BlockchainRid, Set<XPeerID>>) {
    lateinit var contextCreator: (BlockchainRid) -> WorkerContext
}