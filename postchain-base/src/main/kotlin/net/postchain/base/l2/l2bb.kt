// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.l2

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.LeafStore
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.common.data.Hash
import net.postchain.core.*
import net.postchain.crypto.DigestSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvEncoder
import java.util.*

interface L2EventProcessor {
    fun emitL2Event(evt: Gtv)
    fun emitL2State(state_n: Long, state: Gtv)
}

interface L2Implementation: L2EventProcessor {
    fun init(blockEContext: BlockEContext)
    fun finalize(): Map<String, Gtv>
}

class L2BlockBuilder(blockchainRID: BlockchainRid,
                     cryptoSystem: CryptoSystem,
                     eContext: EContext,
                     store: BlockStore,
                     txFactory: TransactionFactory,
                     specialTxHandler: SpecialTransactionHandler,
                     subjects: Array<ByteArray>,
                     blockSigMaker: SigMaker,
                     blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
                     usingHistoricBRID: Boolean,
                     private val l2Implementation: L2Implementation,
                     maxBlockSize: Long = 20 * 1024 * 1024, // 20mb
                     maxBlockTransactions: Long = 100
): BaseBlockBuilder(blockchainRID, cryptoSystem, eContext, store, txFactory, specialTxHandler, subjects, blockSigMaker,
        blockchainRelatedInfoDependencyList, usingHistoricBRID,
        maxBlockSize, maxBlockTransactions), L2EventProcessor by l2Implementation {

    override fun begin(partialBlockHeader: BlockHeader?) {
        super.begin(partialBlockHeader)
        l2Implementation.init(bctx)
    }

    override fun getExtraData(): Map<String, Gtv> {
        return l2Implementation.finalize()
    }

    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        if ("l2_event" == type) {
            l2Implementation.emitL2Event(data)
        } else if ("l2_state" == type) {
            val account = data[0].asInteger()
            val state = data[1]
            l2Implementation.emitL2State(account, state)
        }
    }
}

class EthereumL2Implementation(
    private val ds: DigestSystem,
    private val levelsPerPage: Int): L2Implementation {

    private lateinit var bctx: BlockEContext
    lateinit var store: LeafStore
    lateinit var snapshot: SnapshotPageStore
    lateinit var event: EventPageStore

    val events = mutableListOf<Hash>()
    val states = TreeMap<Long, Hash>()

    override fun init(blockEContext: BlockEContext) {
        bctx = blockEContext
        store = LeafStore()
        snapshot = SnapshotPageStore(blockEContext, levelsPerPage, ds)
        event = EventPageStore(blockEContext, levelsPerPage, ds)
    }

    /**
     * Compute event (as a simple Merkle tree) and state hashes (using updateSnapshot)
     */
    override fun finalize(): Map<String, Gtv> {
        val extra = mutableMapOf<String, Gtv>()
        val stateRootHash = snapshot.updateSnapshot(bctx.height, states)
        val eventRootHash = event.writeEventTree(bctx.height, events)
        extra["l2RootState"] = GtvByteArray(stateRootHash)
        extra["l2RootEvent"] = GtvByteArray(eventRootHash)
        return extra
    }

    /**
     * Serialize, write to leaf store, hash using keccak256.
     * Hashes are remembered and later combined into a Merkle tree
     */
    override fun emitL2Event(evt: Gtv) {
        val data = GtvEncoder.simpleEncodeGtv(evt)
        val hash = ds.digest(data)
        events.add(hash)
        store.writeEvent(bctx, hash, data)
    }

    /**
     * Serialize, write to leaf store,
     * hash using keccak256. (state_n, hash) pairs are submitted to updateSnapshot
     * during finalization
     */
    override fun emitL2State(state_n: Long, state: Gtv) {
        val data = GtvEncoder.simpleEncodeGtv(state)
        val hash = ds.digest(data)
        states[state_n] = hash
        store.writeState(bctx, state_n, data)
    }
}