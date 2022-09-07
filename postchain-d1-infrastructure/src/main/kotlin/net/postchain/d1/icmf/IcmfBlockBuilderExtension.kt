package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

const val ICMF_EVENT_TYPE = "icmf"
const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var blockEContext: BlockEContext

    private val queuedEvents = mutableListOf<Pair<Long, IcmfMessage>>()

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        this.blockEContext = blockEContext
        val baseBB = bb as BaseBlockBuilder
        baseBB.installEventProcessor(ICMF_EVENT_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        logger.info { "ICMF message sent" }
        queuedEvents.add(ctxt.txIID to IcmfMessage.fromGtv(data))
    }

    override fun finalize(): Map<String, Gtv> {
        DatabaseAccess.of(blockEContext).apply {
            val queryRunner = QueryRunner()
            val prevMessageBlockHeight = queryRunner.query(blockEContext.conn,
                "SELECT block_height FROM ${tableMessages(blockEContext)} ORDER BY block_height DESC LIMIT 1",
                ScalarHandler<Long>()) ?: -1

            for (event in queuedEvents) {
                queryRunner.update(blockEContext.conn,
                    """INSERT INTO ${tableMessages(blockEContext)}(block_height, prev_message_block_height, tx_iid, topic, body) 
                           VALUES(?, ?, ?, ?, ?)""",
                    blockEContext.height, prevMessageBlockHeight, event.first, event.second.topic, GtvEncoder.encodeGtv(event.second.body))
            }
        }
        return mapOf(ICMF_BLOCK_HEADER_EXTRA to gtv("hashhash")) // TODO return sensible data for block header
    }
}
