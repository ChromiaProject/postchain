// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.message

import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.ebft.message.NullableGtv.gtvToNullableByteArray
import net.postchain.ebft.message.NullableGtv.nullableByteArrayToGtv
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv

class Transaction(val data: ByteArray) : EbftMessage(MessageTopic.TX) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(data))
    }
}

class Signature(val subjectID: ByteArray, val data: ByteArray) : EbftMessage(MessageTopic.SIG) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(subjectID), gtv(data))
    }
}

class BlockSignature(val blockRID: ByteArray, val sig: Signature) : EbftMessage(MessageTopic.BLOCKSIG) {

    override fun toGtv(): GtvArray {
        return gtv(topic.toGtv(), gtv(blockRID),
                gtv(sig.subjectID), gtv(sig.data))
    }
}

class GetBlockSignature(val blockRID: ByteArray) : EbftMessage(MessageTopic.GETBLOCKSIG) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(blockRID))
    }
}

class BlockData(val header: ByteArray, val transactions: List<ByteArray>) : EbftMessage(MessageTopic.BLOCKDATA) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(header),
                gtv(transactions.map { gtv(it) }))
    }
}

class CompleteBlock(val data: BlockData, val height: Long, val witness: ByteArray) : EbftMessage(MessageTopic.COMPLETEBLOCK) {

    companion object {

        fun buildFromBlockDataWithWitness(height: Long, blockData: BlockDataWithWitness): CompleteBlock {
            return CompleteBlock(
                    BlockData(blockData.header.rawData, blockData.transactions),
                    height,
                    blockData.witness.getRawData()
            )
        }

        fun buildFromGtv(data: GtvArray, arrOffset: Int): CompleteBlock {

            return CompleteBlock(
                    BlockData(data[0 + arrOffset].asByteArray(),
                            data[1 + arrOffset].asArray().map { it.asByteArray() }
                    ),
                    data[2 + arrOffset].asInteger(),
                    data[3 + arrOffset].asByteArray()
            )
        }
    }

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(),
                gtv(data.header), gtv(data.transactions.map { gtv(it) }),
                gtv(height), gtv(witness))
    }
}

class GetBlockAtHeight(val height: Long) : EbftMessage(MessageTopic.GETBLOCKATHEIGHT) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(height))
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray) : EbftMessage(MessageTopic.GETUNFINISHEDBLOCK) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(blockRID))
    }
}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>) : EbftMessage(MessageTopic.UNFINISHEDBLOCK) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(header), gtv(transactions.map { gtv(it) }))
    }
}

class Identification(val pubKey: ByteArray, val blockchainRID: BlockchainRid, val timestamp: Long) : EbftMessage(MessageTopic.ID) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(pubKey), gtv(blockchainRID), gtv(timestamp))
    }
}

class Status(
        val blockRID: ByteArray?,
        val height: Long,
        val revolting: Boolean,
        val round: Long,
        val serial: Long,
        val state: Int
) : EbftMessage(MessageTopic.STATUS) {

    companion object {
        fun fromGtv(gtv: GtvArray): Status = Status(
                gtvToNullableByteArray(gtv[1]),
                gtv[2].asInteger(),
                gtv[3].asBoolean(),
                gtv[4].asInteger(),
                gtv[5].asInteger(),
                gtv[6].asInteger().toInt()
        )
    }

    override fun toGtv(): Gtv = gtv(
            topic.toGtv(),
            nullableByteArrayToGtv(blockRID),
            gtv(height),
            gtv(revolting),
            gtv(round),
            gtv(serial),
            gtv(state.toLong())
    )
}

/**
 * Requests that the peer reply with two messages
 *
 * 1. The BlockHeader at height
 * 2. The UnfinishedBlock at height
 *
 * If the peer doesn't have that block, it will reply with the BlockHeader of its tip
 */
class GetBlockHeaderAndBlock(val height: Long) : EbftMessage(MessageTopic.GETBLOCKHEADERANDBLOCK) {
    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(height))
    }
}

/**
 * This represent a block header at a requested height or the highest blockheader
 * if requestedHeight didn't exist. A BlockHeader with empty header and witness byte
 * arrays represents that the node has no blocks at all.
 */
class BlockHeader(val header: ByteArray, val witness: ByteArray, val requestedHeight: Long)
    : EbftMessage(MessageTopic.BLOCKHEADER) {
    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(header), gtv(witness), gtv(requestedHeight))
    }
}

/**
 * Requests that the peer replies with a BlockRange beginning at "height"
 *
 * @property startAtHeight is the height of the first block we want to get (and then we also want any
 * existing blocks after this)
 */
class GetBlockRange(val startAtHeight: Long) : EbftMessage(MessageTopic.GETBLOCKRANGE) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(startAtHeight))
    }

}

/**
 * Holds a number of blocks from "height" and onwards
 *
 * @property startAtHeight is the height of the first block in the range
 * @property isFull "true" means that we have more blocks but couldn't fit them in the package
 * @property blocks is a list of [CompleteBlock]
 */
class BlockRange(val startAtHeight: Long, val isFull: Boolean, val blocks: List<CompleteBlock>) : EbftMessage(MessageTopic.BLOCKRANGE) {

    companion object {

        /**
         * Consumes the raw GTV incoming data
         */
        fun buildFromGtv(data: GtvArray): BlockRange {
            val startAtHeight = data[1].asInteger()
            val isFull = data[2].asBoolean()
            val blocks = mutableListOf<CompleteBlock>()

            val gtvBlockArr = data[3]
            for (gtvThing in gtvBlockArr.asArray()) {
                var blockGtv = gtvThing as GtvArray
                blocks.add(CompleteBlock.buildFromGtv(blockGtv, 0))
            }

            return BlockRange(startAtHeight, isFull, blocks)
        }
    }

    override fun toGtv(): Gtv {
        val gtvBlockList = mutableListOf<Gtv>()
        for (block in blocks) {
            val gtvBlock = gtv(completeBlockToGtv(block.data, block.height, block.witness))
            gtvBlockList.add(gtvBlock)
        }
        return gtv(topic.toGtv(), gtv(startAtHeight), gtv(isFull), gtv(gtvBlockList))
    }

}

/**
 * Message to inform other nodes about which config our node is currently using.
 *
 * @property configHash is the hash of the config currently used
 * @property height is the current height
 */
class AppliedConfig(val configHash: ByteArray, val height: Long) : EbftMessage(MessageTopic.APPLIEDCONFIG) {

    override fun toGtv(): Gtv {
        return gtv(topic.toGtv(), gtv(configHash), gtv(height))
    }

}

/**
 * We do it this way since we don't want to store the "topic" of the [CompleteBlock] message
 */
fun completeBlockToGtv(data: BlockData, height: Long, witness: ByteArray): List<Gtv> {
    return listOf(
            gtv(data.header),
            gtv(data.transactions.map { gtv(it) }),
            gtv(height),
            gtv(witness)
    )
}

