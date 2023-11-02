package net.postchain.ebft.message

import net.postchain.common.BlockchainRid
import net.postchain.core.BadMessageException
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.network.common.LazyPacket

// Whenever an Ebft message is changed, in a non-backward compatible way,
// bump the version and handle old versions accordingly
const val EBFT_VERSION: Long = 1

abstract class EbftMessage(val topic: MessageTopic) {

    companion object {
        fun decode(bytes: ByteArray, @Suppress("UNUSED_PARAMETER") ebftVersion: Long): EbftMessage {
            // TODO: Use the version and inject it and parse messages correctly
            val data = GtvDecoder.decodeGtv(bytes) as GtvArray
            return when (val topic = data[0].asInteger().toInt()) {
                MessageTopic.ID.value -> Identification(data[1].asByteArray(), BlockchainRid(data[2].asByteArray()), data[3].asInteger())
                MessageTopic.STATUS.value -> Status.fromGtv(data)
                MessageTopic.TX.value -> Transaction(data[1].asByteArray())
                MessageTopic.BLOCKSIG.value -> BlockSignature(data[1].asByteArray(), Signature(data[2].asByteArray(), data[3].asByteArray()))
                MessageTopic.GETBLOCKSIG.value -> GetBlockSignature(data[1].asByteArray())
                MessageTopic.COMPLETEBLOCK.value -> CompleteBlock.buildFromGtv(data, 1)
                MessageTopic.GETBLOCKATHEIGHT.value -> GetBlockAtHeight(data[1].asInteger())
                MessageTopic.GETUNFINISHEDBLOCK.value -> GetUnfinishedBlock(data[1].asByteArray())
                MessageTopic.UNFINISHEDBLOCK.value -> UnfinishedBlock(data[1].asByteArray(), data[2].asArray().map { it.asByteArray() })
                MessageTopic.GETBLOCKHEADERANDBLOCK.value -> GetBlockHeaderAndBlock(data[1].asInteger())
                MessageTopic.BLOCKHEADER.value -> BlockHeader(data[1].asByteArray(), data[2].asByteArray(), data[3].asInteger())
                MessageTopic.GETBLOCKRANGE.value -> GetBlockRange(data[1].asInteger())
                MessageTopic.BLOCKRANGE.value -> BlockRange.buildFromGtv(data)
                MessageTopic.APPLIEDCONFIG.value -> AppliedConfig(data[1].asByteArray(), data[2].asInteger())
                MessageTopic.EBFTVERSION.value -> EbftVersion(data[1].asInteger())
                else -> throw BadMessageException("Message topic $topic is not handled")
            }
        }

        inline fun <reified T : EbftMessage> decodeAs(bytes: ByteArray, ebftVersion: Long): T {
            return decode(bytes, ebftVersion) as T
        }
    }

    fun encoded(ebftVersion: Long): LazyPacket = lazy { GtvEncoder.encodeGtv(toGtv(ebftVersion)) }

    abstract fun toGtv(ebftVersion: Long): Gtv

    override fun toString(): String {
        return this::class.simpleName!!
    }
}