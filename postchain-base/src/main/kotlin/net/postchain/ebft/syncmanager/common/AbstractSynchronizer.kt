package net.postchain.ebft.syncmanager.common

import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.worker.WorkerContext

abstract class AbstractSynchronizer(
    val workerContext: WorkerContext,
    val params: FastSyncParameters
) : Messaging(workerContext.engine.getBlockQueries(), workerContext.communicationManager) {

    protected val blockchainConfiguration = workerContext.engine.getConfiguration()
    protected val configuredPeers = workerContext.peerCommConfiguration.networkNodes.getPeerIds()

    protected val peerStatuses = PeerStatuses(params)

    var blockHeight: Long = blockQueries.getBestHeight().get()

    /**
     * Send message to node including the block at [height]. This is a response to the [GetBlockAtHeight] request.
     *
     * @param peerId NodeRid of receiving node
     * @param height requested block height
    fun sendBlockAtHeight(peerId: NodeRid, height: Long) {
        val blockData = blockQueries.getBlockAtHeight(height).get()
        if (blockData == null) {
            logger.debug { "No block at height $height, as requested by $peerId" }
            return
        }
        val packet = CompleteBlock(
            BlockData(blockData.header.rawData, blockData.transactions),
            height,
            blockData.witness.getRawData()
        )
        communicationManager.sendPacket(packet, peerId)
    }
     */
}