package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.Signature
import net.postchain.ebft.message.BlockHeader
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.UnfinishedBlock
import net.postchain.network.CommunicationManager

abstract class Messaging(
        val blockQueries: BlockQueries,
        val communicationManager: CommunicationManager<EbftMessage>,
        private val blockPacker: BlockPacker
) {
    companion object : KLogging()

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
            logger.debug { "No block at height $height, as requested by $peerId" }
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
        val blocks = mutableListOf<CompleteBlock>()
        val allBlocksFit = blockPacker.packBlockRange(
                peerId,
                communicationManager.getPeerPacketVersion(peerId),
                startAtHeight,
                myHeight,
                ::simpleGetBlockAtHeight,
                CompleteBlock::buildFromBlockDataWithWitness,
                blocks)
        val packet = BlockRange(startAtHeight, !allBlocksFit, blocks.toList())
        communicationManager.sendPacket(packet, peerId)
    }

    // Just a wrapper
    private fun simpleGetBlockAtHeight(height: Long): BlockDataWithWitness? {
        return blockQueries.getBlockAtHeight(height).get()
    }

    fun sendBlockHeaderAndBlock(peerID: NodeRid, requestedHeight: Long, myHeight: Long) {
        logger.trace { "GetBlockHeaderAndBlock from peer $peerID for height $requestedHeight, myHeight is $myHeight" }

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
            val block = blockQueries.getBlockAtHeight(myHeight, false).get()
                    ?: throw ProgrammerMistake("Block at height: $myHeight doesn't exist.")
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
        logger.trace { "Replying with UnfinishedBlock to peer $peerID for height $requestedHeight" }
        communicationManager.sendPacket(unfinishedBlock, peerID)
    }

    private fun sendHeader(peerID: NodeRid, header: ByteArray, witness: ByteArray, sentHeight: Long, requestedHeight: Long): BlockHeader {
        val h = BlockHeader(header, witness, requestedHeight)
        logger.trace { "Replying with BlockHeader at height $sentHeight to peer $peerID for requested height $requestedHeight" }
        communicationManager.sendPacket(h, peerID)
        return h
    }

    fun sendBlockSignature(peerID: NodeRid, blockRID: ByteArray) {
        val blockSignature = blockQueries.getBlockSignature(blockRID)
        blockSignature.whenComplete { signature, error ->
            if (error == null) {
                val packet = BlockSignature(blockRID, Signature(signature.subjectID, signature.data))
                communicationManager.sendPacket(packet, peerID)
            } else {
                logger.debug(error) { "Error sending BlockSignature" }
            }
        }
    }
}
