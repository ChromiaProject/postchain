// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.l2

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.*
import net.postchain.gtv.Gtv

interface L2EventProcessor {
    fun emitL2Event(evt: Gtv)
    fun emitL2State(state_n: Long, state: Gtv)
}

interface L2Implementation : L2EventProcessor {
    fun finalize(): Map<String, Gtv>
}

open class L2TxEContext(
        protected val ectx: TxEContext, protected val bb: L2BlockBuilder
) : TxEContext by ectx, L2EventProcessor {

    val events = mutableListOf<Gtv>()
    val states = mutableListOf<Pair<Long, Gtv>>()

    override fun emitL2Event(evt: Gtv) {
        events.add(evt)
    }

    override fun emitL2State(state_n: Long, state: Gtv) {
        states.add(Pair(state_n, state))
    }

    override fun done() {
        for (evt in events) bb.emitL2Event(evt)
        for ((state_n, state) in states) bb.emitL2State(state_n, state)
    }

    override fun <T> getInterface(c: Class<T>): T? {
        if (c == L2TxEContext::class.java) {
            return this as T?
        } else
            return super.getInterface(c)
    }

    companion object {
        fun emitL2Event(ectx: BlockEContext, evt: Gtv) {
            val l2Ctx = ectx.queryInterface<L2TxEContext>()
            if (l2Ctx == null) {
                throw ProgrammerMistake("Blockchain configuration does not support L2")
            } else {
                l2Ctx.emitL2Event(evt)
            }
        }

        fun emitL2AccountState(ectx: BlockEContext, state_n: Long, state: Gtv) {
            val l2Ctx = ectx.queryInterface<L2TxEContext>()
            if (l2Ctx == null) {
                throw ProgrammerMistake("Blockchain configuration does not support L2")
            } else {
                l2Ctx.emitL2State(state_n, state)
            }
        }
    }
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
                     val l2Implementation: L2Implementation,
                     maxBlockSize: Long = 20 * 1024 * 1024, // 20mb
                     maxBlockTransactions: Long = 100
) : BaseBlockBuilder(blockchainRID, cryptoSystem, eContext, store, txFactory, specialTxHandler, subjects, blockSigMaker,
        blockchainRelatedInfoDependencyList, usingHistoricBRID,
        maxBlockSize, maxBlockTransactions), L2EventProcessor by l2Implementation {

    override fun getExtraData(): Map<String, Gtv> {
        return l2Implementation.finalize()
    }
}