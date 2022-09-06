package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.gtv.Gtv

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension, TxEventSink {
    companion object : KLogging()

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        logger.info { "ICMF message sent" }
        // TODO "Not yet implemented"
    }

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        val baseBB = bb as BaseBlockBuilder
        baseBB.installEventProcessor("icmf", this)
    }

    override fun finalize(): Map<String, Gtv> {
        // TODO "Not yet implemented"
        return mapOf()
    }
}
