package net.postchain.ebft.syncmanager.common

import net.postchain.base.BaseBlockHeader
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.core.block.BlockHeader
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.worker.WorkerContext
import java.util.concurrent.CompletableFuture

abstract class AbstractSynchronizer(
        val workerContext: WorkerContext
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager) {

    protected val blockchainConfiguration = workerContext.engine.getConfiguration()
    protected val configuredPeers = workerContext.peerCommConfiguration.networkNodes.getPeerIds()

    // this is used to track pending asynchronous BlockDatabase.addBlock tasks to make sure failure to commit propagates properly
    protected var addBlockCompletionFuture: CompletableFuture<Unit>? = null

    var blockHeight: Long = blockQueries.getBestHeight().get()


    protected fun getHeight(header: BlockHeader): Long {
        // A bit ugly hack. Figure out something better. We shouldn't rely on specific
        // implementation here.
        // Our current implementation, BaseBlockHeader, includes the height, which
        // means that we can trust the height in the header because it's been
        // signed by a quorum of signers.
        // If another BlockHeader implementation is used, that doesn't include
        // the height, we'd have to rely on something else, for example
        // sending the height explicitly, but then we trust only that single
        // sender node to tell the truth.
        // For now we rely on the height being part of the header.
        if (header !is BaseBlockHeader) {
            throw ProgrammerMistake("Expected BaseBlockHeader")
        }
        return header.blockHeaderRec.getHeight()
    }

    protected val procName = BlockchainProcessName(
            workerContext.appConfig.pubKey,
            blockchainConfiguration.blockchainRid
    )
}