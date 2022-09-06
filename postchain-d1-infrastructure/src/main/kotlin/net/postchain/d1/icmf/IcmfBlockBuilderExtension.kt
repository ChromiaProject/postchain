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
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

const val ICMF_EVENT_TYPE = "icmf"

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    private lateinit var blockEContext: BlockEContext

    private val queuedEvents = mutableListOf<Pair<Long, Gtv>>()

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        this.blockEContext = blockEContext
        val baseBB = bb as BaseBlockBuilder
        baseBB.installEventProcessor(ICMF_EVENT_TYPE, this)
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        logger.info { "ICMF message sent" }
        queuedEvents.add(ctxt.txIID to data)
    }

    override fun finalize(): Map<String, Gtv> {
        DatabaseAccess.of(blockEContext).apply {
            val queryRunner = QueryRunner()
            val prevMessageBlockHeight = queryRunner.query(blockEContext.conn,
                "SELECT block_height FROM ${tableMessages(blockEContext)} ORDER BY block_height DESC LIMIT 1",
                ScalarHandler<Long>()) ?: -1

            for (event in queuedEvents) {
                queryRunner.update(blockEContext.conn,
                    """INSERT INTO ${tableMessages(blockEContext)}(block_height, prev_message_block_height, tx_iid, body) 
                           VALUES(?, ?, ?, ?)""",
                    blockEContext.height, prevMessageBlockHeight, event.first, GtvEncoder.encodeGtv(event.second))
            }
        }
        return mapOf() // TODO return data for block header
    }
}
