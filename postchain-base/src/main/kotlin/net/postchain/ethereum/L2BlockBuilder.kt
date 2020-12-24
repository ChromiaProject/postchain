package net.postchain.ethereum

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockHeader
import net.postchain.core.BlockStore
import net.postchain.core.EContext
import net.postchain.core.TransactionFactory
import net.postchain.gtv.Gtv

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
    private val l2Events = mutableListOf<ByteArray>()

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