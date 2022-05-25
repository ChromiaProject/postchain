// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.eif

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.snapshot.DigestSystem
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.LeafStore
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import java.util.*

const val EIF_EVENT = "eif_event"
const val EIF_STATE = "eif_state"

class EthereumEifImplementation(
        private val ds: DigestSystem,
        private val levelsPerPage: Int): BaseBlockBuilderExtension, TxEventSink {

    private lateinit var bctx: BlockEContext
    lateinit var store: LeafStore
    lateinit var snapshot: SnapshotPageStore
    lateinit var event: EventPageStore

    private val events = mutableListOf<Hash>()
    private val states = TreeMap<Long, Hash>()

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        when (type) {
            EIF_EVENT -> emitEifEvent(data)
            EIF_STATE -> emitEifState(data[0].asInteger(), data[1])
            else -> throw ProgrammerMistake("Unrecognized event")
        }
    }

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        baseBB.installEventProcessor(EIF_EVENT, this)
        baseBB.installEventProcessor(EIF_STATE, this)
        bctx = blockEContext
        store = LeafStore()
        snapshot = SnapshotPageStore(blockEContext, levelsPerPage, ds, PREFIX)
        event = EventPageStore(blockEContext, levelsPerPage, ds, PREFIX)
    }

    /**
     * Compute event (as a simple Merkle tree) and state hashes (using updateSnapshot)
     */
    override fun finalize(): Map<String, Gtv> {
        val extra = mutableMapOf<String, Gtv>()
        val stateRootHash = snapshot.updateSnapshot(bctx.height, states)
        val eventRootHash = event.writeEventTree(bctx.height, events)
        extra[EIF] = GtvByteArray(eventRootHash + stateRootHash)
        return extra
    }

    /**
     * Serialize, write to leaf store, hash using keccak256.
     * Hashes are remembered and later combined into a Merkle tree
     */
    private fun emitEifEvent(evt: Gtv) {
        val data = SimpleGtvEncoder.encodeGtv(evt)
        val hash = ds.digest(data)
        store.writeEvent(bctx, PREFIX, events.size.toLong(), hash, data)
        events.add(hash)
    }

    /**
     * Serialize, write to leaf store,
     * hash using keccak256. (state_n, hash) pairs are submitted to updateSnapshot
     * during finalization
     */
    private fun emitEifState(state_n: Long, state: Gtv) {
        val data = SimpleGtvEncoder.encodeGtv(state)
        val hash = ds.digest(data)
        states[state_n] = hash
        store.writeState(bctx, PREFIX, state_n, data)
    }
}