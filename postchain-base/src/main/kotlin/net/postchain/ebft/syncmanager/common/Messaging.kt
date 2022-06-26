package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockQueries
import net.postchain.ebft.message.*
import net.postchain.network.CommunicationManager
import net.postchain.network.MAX_PAYLOAD_SIZE

abstract class Messaging(val blockQueries: BlockQueries, val communicationManager: CommunicationManager<EbftMessage>) {
    companion object: KLogging() {

        /**
         * We don't want to consume the maximum package size with content, so we reduce the maximum package size with
         * a few megabytes to make certain we'll be ok.
         * NOTE: The default max blocksize is 26M, and the default package size is 30M, so we'll make sure we save
         * as much space for "extra things" like we would for a maximum block.
         */
        val MAX_PACKAGE_CONTENT_BYTES = MAX_PAYLOAD_SIZE - 4_000_000
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
     * @param peerId NodeRid of receiving node
     * @param height requested block height
     */
    fun sendBlockAtHeight(peerId: NodeRid, height: Long) {
        val blockData = blockQueries.getBlockAtHeight(height).get()
        if (blockData == null) {
            logger.debug{ "No block at height $height, as requested by $peerId" }
            return
        }
        val packet = CompleteBlock.buildFromBlockDataWithWitness(height, blockData)
        communicationManager.sendPacket(packet, peerId)
    }

    /**
     * Send message to node including the block at [startAtHeight] and onwards (max 10). This is a response to the [GetBlockRange] request.
     *
     * Rules:
     * - Even if we don't find any block we still send a package back
     * - If we have more blocks but reach the size limit, we don't add more blocks but set the "isFull" flag.
     * - We never send more than 10 blocks
     *
     * @param peerId NodeRid of receiving node
     * @param startAtHeight requested block height to start from
     */
    fun sendBlockRangeFromHeight(peerId: NodeRid, startAtHeight: Long, myHeight: Long) {
        logger.trace{ "GetBlockRange from peer $peerId , start at height $startAtHeight, myHeight is $myHeight" }

        val blocks = mutableListOf<CompleteBlock>()
        var height = -1L
        var goOn = true
        var isFull = false
        var totByteSize = 0
        var blocksAdded = 0

        while (goOn && !isFull && blocksAdded < 10) {
            height = startAtHeight + blocksAdded
            logger.debug { "GetBlockRange from peer $peerId , height $height, myHeight is $myHeight" }
            val blockData = blockQueries.getBlockAtHeight(height).get()
            if (blockData == null) {
                logger.trace { "GetBlockRange no more blocks in DB."}
                goOn = false
            } else {
                val completeBlock = CompleteBlock.buildFromBlockDataWithWitness(height, blockData)

                // We are not allowed to send packages above the size limit
                totByteSize += completeBlock.encoded.size

                if (totByteSize < MAX_PACKAGE_CONTENT_BYTES) {
                    logger.trace { "GetBlockRange block found in DB."}
                    blocks.add(completeBlock)
                    blocksAdded++
                } else {
                    isFull = true
                    // Should be an unusual message, b/c blocks are usually small
                    logger.debug { "GetBlockRange block found in DB but could not fit more than ${blocks.size} blocks into this BlockRange message." }
                }
            }
        }

        val packet = BlockRange(startAtHeight, isFull, blocks.toList())
        communicationManager.sendPacket(packet, peerId)
    }


    fun sendBlockHeaderAndBlock(peerID: NodeRid, requestedHeight: Long, myHeight: Long) {
        logger.trace{ "GetBlockHeaderAndBlock from peer $peerID for height $requestedHeight, myHeight is $myHeight" }

        if (myHeight == -1L) {
            sendHeader(peerID, byteArrayOf(), byteArrayOf(), -1, requestedHeight)
            return
        }

        if (myHeight < requestedHeight) {
            if (tipHeight == myHeight) {
                // We have a cached header. Make a copy with the correct requested height and send the copy.
                sendHeader(peerID, tipHeader.header, tipHeader.witness, tipHeight, requestedHeight)
                return
            }
            val block = blockQueries.getBlockAtHeight(myHeight, false).get()!!
            val h = sendHeader(peerID, block.header.rawData, block.witness.getRawData(), myHeight, requestedHeight)
            tipHeader = h
            tipHeight = myHeight
            return
        }

        val blockData = blockQueries.getBlockAtHeight(requestedHeight).get()!!
        val header = sendHeader(peerID, blockData.header.rawData, blockData.witness.getRawData(), requestedHeight, requestedHeight)
        if (requestedHeight == myHeight) {
            tipHeight = myHeight
            tipHeader = header
        }

        val unfinishedBlock = UnfinishedBlock(blockData.header.rawData, blockData.transactions)
        logger.trace{ "Replying with UnfinishedBlock to peer $peerID for height $requestedHeight" }
        communicationManager.sendPacket(unfinishedBlock, peerID)
    }

    private fun sendHeader(peerID: NodeRid, header: ByteArray, witness: ByteArray, sentHeight: Long, requestedHeight: Long): BlockHeader {
        val h = BlockHeader(header, witness, requestedHeight)
        logger.trace{ "Replying with BlockHeader at height $sentHeight to peer $peerID for requested height $requestedHeight" }
        communicationManager.sendPacket(h, peerID)
        return h
    }
}