package net.postchain.ebft.syncmanager.readonly

import mu.KLogging
import net.postchain.concurrent.util.get
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockHeaderAndBlock
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.GetBlockSignature
import net.postchain.ebft.syncmanager.common.BlockPacker
import net.postchain.ebft.syncmanager.common.Messaging
import net.postchain.network.CommunicationManager

class ForceReadOnlyMessageProcessor(
        blockQueries: BlockQueries,
        communicationManager: CommunicationManager<EbftMessage>,
        val lastBlockHeight: Long
) : Messaging(blockQueries, communicationManager, BlockPacker) {

    companion object : KLogging()

    fun processMessages() {
        for ((peerId, _, message) in communicationManager.getPackets()) {
            try {
                when (message) {
                    is GetBlockAtHeight -> if (message.height <= lastBlockHeight) sendBlockAtHeight(peerId, message.height)
                    is GetBlockRange -> sendBlockRangeFromHeight(peerId, message.startAtHeight, lastBlockHeight) // A replica might ask us
                    is GetBlockHeaderAndBlock -> sendBlockHeaderAndBlock(peerId, message.height, lastBlockHeight)
                    is GetBlockSignature -> {
                        val height = blockQueries.getBlock(message.blockRID, true).get()?.height ?: -1L
                        if (height != -1L && height <= lastBlockHeight) sendBlockSignature(peerId, message.blockRID)
                    }

                    else -> logger.debug { "Unhandled message type: ${message.topic} from peer $peerId" }
                }
            } catch (e: Exception) {
                logger.info("Couldn't handle message $message from peer $peerId. Ignoring and continuing", e)
            }
        }
    }
}
