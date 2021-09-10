package net.postchain.base.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.snapshot.LeafStore
import net.postchain.common.data.Hash
import net.postchain.core.BlockEContext
import net.postchain.core.ProgrammerMistake
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv

/**
 *
 * This class implements both the BBB Extension and the [TxEventSink], so that the extension, during initialization,
 * can add itself as an event listener (i.e. [TxEventSink]). After we are added as event listener,
 * we only care about events.
 *
 * There is only one event type we care about: "icmf_message_event"
 * This one we must save.
 */
class IcmfBBBExtension : BaseBlockBuilderExtension, TxEventSink {

    private lateinit var bctx: BlockEContext
    lateinit var store: LeafStore // We only need leaf store, since we consider every ICMF message as a solo message.

    private val events = mutableListOf<Hash>()

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        when (type) {
            "icmf_message_event" -> icmfEvent(data)
            else -> throw ProgrammerMistake("Unrecognized event")
        }
    }

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        baseBB.installEventProcessor("icmf_message_event", this)
        bctx = blockEContext
        store = LeafStore()
    }

    override fun finalize(): Map<String, Gtv> {
        val extra = mutableMapOf<String, Gtv>()
        // TODO: Olle
        //val eventRootHash = store.writeEvent()
        //extra["icmfRootEvent"] = GtvByteArray(eventRootHash)
        return extra
    }

    private fun icmfEvent(data: Gtv) {

    }

}