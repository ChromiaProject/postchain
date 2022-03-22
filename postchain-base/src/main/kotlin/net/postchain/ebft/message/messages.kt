// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.message

import net.postchain.common.toHex
import net.postchain.core.BlockchainRid
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

class SignedMessage(val message: ByteArray, val pubKey: ByteArray, val signature: ByteArray) {

    companion object {
        fun decode(bytes: ByteArray): SignedMessage {
            try {
                val gtvArray = GtvDecoder.decodeGtv(bytes) as GtvArray

                return SignedMessage(gtvArray[0].asByteArray(), gtvArray[1].asByteArray(), gtvArray[2].asByteArray())
            } catch (e: Exception) {
                throw UserMistake("bytes ${bytes.toHex()} cannot be decoded", e)
            }
        }
    }

    fun encode(): ByteArray {
        return GtvEncoder.encodeGtv(toGtv())
    }

    fun toGtv(): Gtv {
        return gtv(gtv(message), gtv(pubKey), gtv(signature))
    }
}

class Transaction(val data: ByteArray): Message(MessageTopic.TX) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(data))
    }
}

class Signature(val subjectID: ByteArray, val data: ByteArray): Message(MessageTopic.SIG) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(subjectID), gtv(data))
    }
}

class BlockSignature(val blockRID: ByteArray, val sig: Signature): Message(MessageTopic.BLOCKSIG) {

    override fun toGtv(): GtvArray {
        return gtv(gtv(topic.toLong()), gtv(blockRID),
                gtv(sig.subjectID), gtv(sig.data))
    }
}

class GetBlockSignature(val blockRID: ByteArray): Message(MessageTopic.GETBLOCKSIG) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(blockRID))
    }
}

class BlockData(val header: ByteArray, val transactions: List<ByteArray>): Message(MessageTopic.BLOCKDATA) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(header),
                gtv(transactions.map { gtv(it) }))
    }
}

class CompleteBlock(val data: BlockData, val height: Long, val witness: ByteArray): Message(MessageTopic.COMPLETEBLOCK) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()),
                gtv(data.header), gtv(data.transactions.map { gtv(it) }),
                gtv(height), gtv(witness))
    }
}

class GetBlockAtHeight(val height: Long): Message(MessageTopic.GETBLOCKATHEIGHT) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(height))
    }
}

class GetUnfinishedBlock(val blockRID: ByteArray): Message(MessageTopic.GETUNFINISHEDBLOCK) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(blockRID))
    }
}

class UnfinishedBlock(val header: ByteArray, val transactions: List<ByteArray>): Message(MessageTopic.UNFINISHEDBLOCK) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(header), gtv(transactions.map { gtv(it) }))
    }
}

class Identification(val pubKey: ByteArray, val blockchainRID: BlockchainRid, val timestamp: Long): Message(MessageTopic.ID) {

    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(pubKey), gtv(blockchainRID), gtv(timestamp))
    }
}

class Status(val blockRID: ByteArray?, val height: Long, val revolting: Boolean, val round: Long,
                  val serial: Long, val state: Int): Message(MessageTopic.STATUS) {


    override fun toGtv(): Gtv {
        val currentBlockRid: Gtv = if (blockRID != null) {
            gtv(blockRID)
        } else {
            GtvNull
        }
        return gtv(gtv(topic.toLong()), currentBlockRid, gtv(height),
                gtv(revolting), gtv(round), gtv(serial), gtv(state.toLong()))
    }
}

/**
 * Requests that the peer reply with two messages
 *
 * 1. The BlockHeader at height
 * 2. The UnfinishedBlock at height
 *
 * If the peer doesn't have that block, it will reply with the BlockHeader of its tip
 */
class GetBlockHeaderAndBlock(val height: Long): Message(MessageTopic.GETBLOCKHEADERANDBLOCK) {
    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(height))
    }
}

/**
 * This represent a block header at a requested height or the highest blockheader
 * if requestedHeight didn't exist. A BlockHeader with empty header and witness byte
 * arrays represents that the node has no blocks at all.
 */
class BlockHeader(val header: ByteArray, val witness: ByteArray, val requestedHeight: Long)
    : Message(MessageTopic.BLOCKHEADER) {
    override fun toGtv(): Gtv {
        return gtv(gtv(topic.toLong()), gtv(header), gtv(witness), gtv(requestedHeight))
    }
}



