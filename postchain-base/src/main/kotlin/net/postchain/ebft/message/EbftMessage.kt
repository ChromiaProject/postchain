package net.postchain.ebft.message

import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvNull

abstract class EbftMessage(val topic: MessageTopic) {

    companion object {
        fun decode(bytes: ByteArray): EbftMessage {
            val data =  GtvDecoder.decodeGtv(bytes) as GtvArray
            return when (val topic = data[0].asInteger().toInt()) {
                MessageTopic.ID.value -> Identification(data[1].asByteArray(), BlockchainRid(data[2].asByteArray()), data[3].asInteger())
                MessageTopic.STATUS.value -> Status(nullableBytearray(data[1]), data[2].asInteger(), data[3].asBoolean(), data[4].asInteger(), data[5].asInteger(), data[6].asInteger().toInt())
                MessageTopic.TX.value -> Transaction(data[1].asByteArray())
                MessageTopic.SIG.value -> Signature(data[1].asByteArray(), data[2].asByteArray())
                MessageTopic.BLOCKSIG.value -> BlockSignature(data[1].asByteArray(), Signature(data[2].asByteArray(), data[3].asByteArray()))
                MessageTopic.GETBLOCKSIG.value -> GetBlockSignature(data[1].asByteArray())
                MessageTopic.BLOCKDATA.value -> BlockData(data[1].asByteArray(), data[2].asArray().map { it.asByteArray() })
                MessageTopic.COMPLETEBLOCK.value -> CompleteBlock(BlockData(data[1].asByteArray(), data[2].asArray().map { it.asByteArray() }), data[3].asInteger(), data[4].asByteArray())
                MessageTopic.GETBLOCKATHEIGHT.value -> GetBlockAtHeight(data[1].asInteger())
                MessageTopic.GETUNFINISHEDBLOCK.value -> GetUnfinishedBlock(data[1].asByteArray())
                MessageTopic.UNFINISHEDBLOCK.value -> UnfinishedBlock(data[1].asByteArray(), data[2].asArray().map { it.asByteArray() })
                MessageTopic.GETBLOCKHEADERANDBLOCK.value -> GetBlockHeaderAndBlock(data[1].asInteger())
                MessageTopic.BLOCKHEADER.value -> BlockHeader(data[1].asByteArray(), data[2].asByteArray(), data[3].asInteger())
                else -> throw BadDataMistake(BadDataType.BAD_MESSAGE, "Message topic $topic is not handled")
            }
        }

        inline fun <reified T : EbftMessage> decodeAs(bytes: ByteArray): T {
           return decode(bytes) as T
        }

        fun nullableBytearray(tmpGtv: Gtv): ByteArray? {
            return when (tmpGtv) {
                is GtvNull -> null
                is GtvByteArray -> tmpGtv.asByteArray()
                else -> throw BadDataMistake(BadDataType.BAD_MESSAGE, "Incorrect EBFT status ${tmpGtv.type}")
            }
        }
    }

    val encoded: ByteArray by lazy { GtvEncoder.encodeGtv(toGtv()) }

    protected abstract fun toGtv(): Gtv

    override fun toString(): String {
        return this::class.simpleName!!
    }
}