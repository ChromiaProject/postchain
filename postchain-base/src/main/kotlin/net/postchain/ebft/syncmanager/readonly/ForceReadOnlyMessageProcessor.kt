package net.postchain.ebft.syncmanager.readonly

import mu.KLogging
import net.postchain.concurrent.util.get
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.syncmanager.common.Messaging
import net.postchain.network.CommunicationManager

class ForceReadOnlyMessageProcessor(blockQueries: BlockQueries, communicationManager: CommunicationManager<EbftMessage>)
    : Messaging(blockQueries, communicationManager) {

    companion object : KLogging()

    var blockHeight: Long = blockQueries.getLastBlockHeight().get()

    fun processMessages() {
        for ((peerId, message) in communicationManager.getPackets()) {
            try {
                when (message) {
                    is GetBlockAtHeight -> sendBlockAtHeight(peerId, message.height)
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, blockHeight) // A replica might ask us
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, blockHeight)
                    is GetBlockSignature -> sendBlockSignature(peerId, message.blockRID)

                    else -> logger.debug { "Unhandled message type: ${message.topic} from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }
}