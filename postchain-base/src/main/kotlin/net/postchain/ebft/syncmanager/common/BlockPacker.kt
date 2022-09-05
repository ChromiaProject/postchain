package net.postchain.ebft.syncmanager.common

import mu.KLogging
import net.postchain.core.NodeRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.ebft.message.CompleteBlock
import net.postchain.network.MAX_PAYLOAD_SIZE

object BlockPacker: KLogging() {

    /**
     * We don't want to consume the maximum package size with content, so we reduce the maximum package size with
     * a few megabytes to make certain we'll be ok.
     * NOTE: The default max blocksize is 26M, and the default package size is 30M, so we'll make sure we save
     * as much space for "extra things" like we would for a maximum block.
     */
    const val MAX_PACKAGE_CONTENT_BYTES = MAX_PAYLOAD_SIZE - 4_000_000
    const val MAX_BLOCKS_IN_PACKAGE = 10

    /**
     * Packs blockchain blocks into the "blocks" list so that it does not go over package size.
     *
     * @return true if all blocks we had could fit in the block
     */
    fun packBlockRange(
        peerId: NodeRid,
        startAtHeight: Long,
        myHeight: Long,
        getBlockFromHeight: (height: Long) -> BlockDataWithWitness?, // Sending this to simplify testing
        buildFromBlockDataWithWitness: (height: Long, blockData: BlockDataWithWitness) -> CompleteBlock, // Sending this to simplify testing
        packedBlocks: MutableList<CompleteBlock> // Holds the "return" list
    ): Boolean {
        var totByteSize = 0
        var blocksAdded = 0
        while (blocksAdded < MAX_BLOCKS_IN_PACKAGE) {
            val height = startAtHeight + blocksAdded
            logger.debug { "GetBlockRange from peer $peerId , height $height, myHeight is $myHeight" }
            val blockData = getBlockFromHeight(height)
            if (blockData == null) {
                logger.debug { "GetBlockRange no more blocks in DB."}
                return true
            } else {
                val completeBlock = buildFromBlockDataWithWitness(height, blockData)

                // We are not allowed to send packages above the size limit
                totByteSize += completeBlock.encoded.size

                if (totByteSize < MAX_PACKAGE_CONTENT_BYTES) {
                    logger.trace { "GetBlockRange block found in DB."}
                    packedBlocks.add(completeBlock)
                    blocksAdded++
                } else {
                    // Should be an unusual message, b/c blocks are usually small
                    logger.debug { "GetBlockRange block found in DB but could not fit more than ${packedBlocks.size} blocks into this BlockRange message." }
                    return false
                }
            }
        }
        return true
    }
}