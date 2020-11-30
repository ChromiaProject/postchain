// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ethereum.L2chain

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.*
import net.postchain.gtv.Gtv

open class L2BlockEContext(
        protected val ectx: BlockEContext, protected val bb: L2BlockBuilder
) : BlockEContext by ectx {
    override fun <T> getInterface(c: Class<T>): T? {
        if (c == L2BlockEContext::class.java) {
            return this as T?
        } else
            return super.getInterface(c)
    }

    fun emitL2Event(evt: ByteArray) {
        bb.appendL2Event(evt)
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
                     maxBlockSize: Long = 20 * 1024 * 1024, // 20mb
                     maxBlockTransactions: Long = 100
): BaseBlockBuilder(blockchainRID, cryptoSystem, eContext, store, txFactory, specialTxHandler, subjects, blockSigMaker,
        blockchainRelatedInfoDependencyList, usingHistoricBRID,
        maxBlockSize, maxBlockTransactions)
{
    val l2Events = mutableListOf<ByteArray>()

    fun appendL2Event(evt: ByteArray) {
        l2Events.add(evt)
    }

    override fun begin(partialBlockHeader: BlockHeader?) {
        super.begin(partialBlockHeader)
        bctx = L2BlockEContext(bctx, this)
    }

    override fun getExtraData(): Map<String, Gtv> {
        return mapOf() // TODO: compute merkle root hash of l2Events
    }


}