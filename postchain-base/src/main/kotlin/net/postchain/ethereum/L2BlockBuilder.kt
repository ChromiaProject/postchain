package net.postchain.ethereum

import net.postchain.base.*
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockHeader
import net.postchain.core.BlockStore
import net.postchain.core.EContext
import net.postchain.core.TransactionFactory
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.merkleHash

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
    private val l2Events = mutableListOf<Gtv>()

    fun appendL2Event(evt: Gtv) {
        l2Events.add(evt)
    }

    override fun begin(partialBlockHeader: BlockHeader?) {
        super.begin(partialBlockHeader)
        bctx = L2BlockEContext(bctx, this)
    }

    override fun getExtraData(): Map<String, Gtv> {
        val events = GtvArray(l2Events.toTypedArray())
        val tree = GtvBinaryTreeFactory().buildFromGtv(events)
        val merkleTree = GtvMerkleProofTreeFactory().buildFromBinaryTree(tree, GtvMerkleHashCalculator(cryptoSystem))
        val merkleRootHash = merkleTree.merkleHash(GtvMerkleHashCalculator(cryptoSystem))
        return mapOf("l2events" to merkleTree.serializeToGtv(), "l2hash" to GtvFactory.gtv(merkleRootHash))
    }

}