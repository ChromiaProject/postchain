package net.postchain.core.block

import net.postchain.base.ConfirmationProofMaterial
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.TxEContext
import net.postchain.crypto.PubKey

interface BlockStore {
    fun beginBlock(ctx: EContext, blockchainRID: BlockchainRid, blockHeightDependencies: Array<Hash?>?): InitialBlockData
    fun addTransaction(bctx: BlockEContext, tx: Transaction, transactionNumber: Long): TxEContext
    fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader)
    fun commitBlock(bctx: BlockEContext, w: BlockWitness)
    fun getBlockHeightFromOwnBlockchain(ctx: EContext, blockRID: ByteArray): Long? // returns null if not found
    fun getBlockHeightFromAnyBlockchain(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? // returns null if not found
    fun getChainId(ctx: EContext, blockchainRID: BlockchainRid): Long? // returns null if not found
    fun getBlockRID(ctx: EContext, height: Long): ByteArray? // returns null if height is out of range
    fun getLastBlockHeight(ctx: EContext): Long // height of the last block, first block has height 0
    fun getLastBlockTimestamp(ctx: EContext): Long
    fun getBlockHeightInfo(ctx: EContext, blockchainRID: BlockchainRid): Pair<Long, Hash>?
    fun getMerkleHashVersion(ctx: EContext, height: Long): Long

    //    fun getBlockData(ctx: EContext, blockRID: ByteArray): BlockData
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray

    fun getBlocksBetweenTimes(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated
    fun getBlocksBetweenHeights(ctx: EContext, heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): BlockDetailsTruncated
    fun getBlocksFromHeight(ctx: EContext, fromHeight: Long, limit: Int): List<DatabaseAccess.BlockInfoExt>
    fun getBlock(ctx: EContext, blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail?
    fun getTransactionInfo(ctx: EContext, txRID: ByteArray): TransactionInfoExt?
    fun getTransactionsInfo(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): TransactionInfoExtsTruncated
    fun getTransactionsInfoBySigner(ctx: EContext, timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): TransactionInfoExtsTruncated
    fun getLastTransactionNumber(ctx: EContext): Long
    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray
    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray?
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray>

    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): ConfirmationProofMaterial?
}
