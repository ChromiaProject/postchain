package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv

const val ICMF_MESSAGE_TYPE = "icmf_message"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem
    private lateinit var blockEContext: BlockEContext

    private val queuedEvents = mutableListOf<SentIcmfMessage>()

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        this.blockEContext = blockEContext
        val baseBB = bb as BaseBlockBuilder
        cryptoSystem = baseBB.cryptoSystem
        baseBB.installEventProcessor(ICMF_MESSAGE_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        val message = SentIcmfMessage.fromGtv(data)
        logger.info("ICMF message sent in topic ${message.topic}")
        queuedEvents.add(message)
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        val hashesByTopic = queuedEvents
            .groupBy { it.topic }
        val hashByTopic = hashesByTopic
            .mapValues {
                TopicHeaderData(cryptoSystem.digest(it.value.map { message ->
                    cryptoSystem.digest(
                        GtvEncoder.encodeGtv(
                            message.body
                        )
                    )
                }.fold(ByteArray(0)) { total, item ->
                    total.plus(
                        item
                    )
                }), it.value.first().previousMessageBlockHeight).toGtv()
            }
        return mapOf(ICMF_BLOCK_HEADER_EXTRA to gtv(hashByTopic))
    }
}
