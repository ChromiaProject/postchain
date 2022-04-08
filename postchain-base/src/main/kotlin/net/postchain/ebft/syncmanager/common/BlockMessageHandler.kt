package net.postchain.ebft.syncmanager.common

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import mu.KLogging
import net.postchain.core.BlockQueries
import net.postchain.ebft.message.*
import net.postchain.network.CommunicationManager
import net.postchain.core.NodeRid
import net.postchain.core.Shutdownable

class BlockMessageHandler(val blockQueries: BlockQueries, val communicationManager: CommunicationManager<EbftMessage>) : Shutdownable {
    companion object: KLogging()

    private var blockMessagesJob: Job

    init {
        blockMessagesJob = CoroutineScope(Dispatchers.Default).launch {
            communicationManager.messages
                .collect {
                    val (nodeId, message) = it
                    when (message) {
                        is GetBlockHeaderAndBlock -> {
                            sendBlockHeaderAndBlock(nodeId, message.height, blockQueries.getBestHeight().get()) // This is expensive
                        }
                        is GetBlockAtHeight -> {
                            sendBlockAtHeight(nodeId, message.height)
                        }
                    }
                }
        }
    }

    override fun shutdown() {
        blockMessagesJob.cancel()
    }

    /**
     * We're going to get a lot of requests from peers in fastsync mode. We should cache our tip
     * to be able to answer quicker and not bother the database with repeated queries.
     *
     * At least the header needs caching because we're sending it very often as a "I'm drained" signal,
     * but the full block might also be a good idea. Let's start with caching the header and see
     * if we need to cache the full block too.
     *
     * This cache sped up the SyncTest with 8 peers and 50 blocks (see SyncTest) from
     * about 40-56 seconds to about 34-47 seconds.
     */
    private var tipHeight: Long = -1
    private var tipHeader: BlockHeader = BlockHeader(byteArrayOf(), byteArrayOf(), 0)

    /**
     * Send message to node including the block at [height]. This is a response to the [GetBlockAtHeight] request.
     *
     * @param toNodeRid NodeRid of receiving node
     * @param height requested block height
     */
    private fun sendBlockAtHeight(toNodeRid: NodeRid, height: Long) {
        val blockData = blockQueries.getBlockAtHeight(height).get()
        if (blockData == null) {
            logger.debug{ "No block at height $height, as requested by $toNodeRid" }
            return
        }
        val packet = CompleteBlock(BlockData(blockData.header.rawData, blockData.transactions), height, blockData.witness.getRawData())
        communicationManager.sendPacket(packet, toNodeRid)
    }

    private fun sendBlockHeaderAndBlock(toNodeRid: NodeRid, requestedHeight: Long, myHeight: Long) {
        logger.trace{ "GetBlockHeaderAndBlock from peer $toNodeRid for height $requestedHeight, myHeight is $myHeight" }

        if (myHeight == -1L) {
            sendHeader(toNodeRid, byteArrayOf(), byteArrayOf(), -1, requestedHeight)
            return
        }

        if (myHeight < requestedHeight) {
            if (tipHeight == myHeight) {
                // We have a cached header. Make a copy with the correct requested height and send the copy.
                sendHeader(toNodeRid, tipHeader.header, tipHeader.witness, tipHeight, requestedHeight)
                return
            }
            val block = blockQueries.getBlockAtHeight(myHeight, false).get()!!
            val h = sendHeader(toNodeRid, block.header.rawData, block.witness.getRawData(), myHeight, requestedHeight)
            tipHeader = h
            tipHeight = myHeight
            return
        }

        val blockData = blockQueries.getBlockAtHeight(requestedHeight).get()!!
        val header = sendHeader(toNodeRid, blockData.header.rawData, blockData.witness.getRawData(), requestedHeight, requestedHeight)
        if (requestedHeight == myHeight) {
            tipHeight = myHeight
            tipHeader = header
        }

        val unfinishedBlock = UnfinishedBlock(blockData.header.rawData, blockData.transactions)
        logger.trace{ "Replying with UnfinishedBlock to peer $toNodeRid for height $requestedHeight" }
        communicationManager.sendPacket(unfinishedBlock, toNodeRid)
    }

    private fun sendHeader(toNodeRid: NodeRid, header: ByteArray, witness: ByteArray, sentHeight: Long, requestedHeight: Long): BlockHeader {
        val h = BlockHeader(header, witness, requestedHeight)
        logger.trace{ "Replying with BlockHeader at height $sentHeight to peer $toNodeRid for requested height $requestedHeight" }
        communicationManager.sendPacket(h, toNodeRid)
        return h
    }
}