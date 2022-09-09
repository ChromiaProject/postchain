package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

const val ICMF_EVENT_TYPE = "icmf"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension(val cryptoSystem: CryptoSystem) : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var blockEContext: BlockEContext

    private val queuedEvents = mutableListOf<Pair<Long, SentIcmfMessage>>()

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        this.blockEContext = blockEContext
        val baseBB = bb as BaseBlockBuilder
        baseBB.installEventProcessor(ICMF_EVENT_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        val message = SentIcmfMessage.fromGtv(data)
        logger.info("ICMF message sent in topic ${message.topic}")
        queuedEvents.add(ctxt.txIID to message)
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        DatabaseAccess.of(blockEContext).apply {
            val queryRunner = QueryRunner()
            val prevMessageBlockHeight = queryRunner.query(
                blockEContext.conn,
                "SELECT MAX(block_height) FROM ${tableMessage(blockEContext)}",
                ScalarHandler<Long>()
            ) ?: -1
            var rowid = queryRunner.query(
                blockEContext.conn,
                "SELECT MAX(rowid) FROM ${tableMessage(blockEContext)}",
                ScalarHandler<Long>()
            ) ?: -1

            for (event in queuedEvents) {
                rowid++
                queryRunner.update(
                    blockEContext.conn,
                    """INSERT INTO ${tableMessage(blockEContext)}(rowid, transaction, block_height, prev_message_block_height, topic, body) 
                           VALUES(?, ?, ?, ?, ?, ?)""",
                    rowid,
                    event.first,
                    blockEContext.height,
                    prevMessageBlockHeight,
                    event.second.topic,
                    GtvEncoder.encodeGtv(event.second.body)
                )
            }
        }
        val hashesByTopic = queuedEvents
            .map { it.second }
            .groupBy { it.topic }
            .mapValues { messages -> messages.value.map { message -> cryptoSystem.digest(GtvEncoder.encodeGtv(message.body)) } }
        val hashByTopic = hashesByTopic
            .mapValues { cryptoSystem.digest(it.value.fold(ByteArray(0)) { total, item -> total.plus(item) }) }
        return mapOf(ICMF_BLOCK_HEADER_EXTRA to gtv(hashByTopic.mapValues { gtv(it.value) }))
    }
}
