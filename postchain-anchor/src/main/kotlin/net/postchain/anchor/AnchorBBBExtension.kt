package net.postchain.anchor

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
 * Anchor headers from other blockchains on this node, and if everything works out, the
 * block will be built.
 *
 * This class implements both the BBB Extension and the [TxEventSink], so that the extension, during initialization,
 * can add itself as an event listener (i.e. [TxEventSink]). After we are added as event listener,
 * we only care about events.
 *
 */
class AnchorBBBExtension : BaseBlockBuilderExtension, TxEventSink {

    private lateinit var bctx: BlockEContext
    lateinit var store: LeafStore // We only need leaf store, since we consider every ICMF message as a solo message.

    private val events = mutableListOf<Hash>()

    /**
     * Cannot really do any validation here, since we get into here after the TX has been committed.
     */
    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        when (type) {
            "anchor_" -> anchorEvent(data)
            else -> throw ProgrammerMistake("Unrecognized event")
        }
    }

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        baseBB.installEventProcessor("anchor_header_message", this)
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

    private fun anchorEvent(data: Gtv) {
        //

    }

}