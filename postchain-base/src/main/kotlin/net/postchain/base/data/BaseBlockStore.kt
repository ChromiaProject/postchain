// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.BaseTxEContext
import net.postchain.base.ConfirmationProofMaterial
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TxEContext
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockStore
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.InitialBlockData

/**
 * Provides database access to the location where the blockchain with related metadata and transactions
 * are stored
 */
class BaseBlockStore : BlockStore {

    companion object : KLogging()


    /**
     * Get initial block data, i.e. data necessary for building the next block
     *
     * @param ctx Connection context
     * @returns Initial block data
     */
    override fun beginBlock(ctx: EContext, blockchainRID: BlockchainRid, blockHeightDependencies: Array<Hash?>?): InitialBlockData {
        val db = DatabaseAccess.of(ctx)
        if (ctx.chainID < 0) {
            throw UserMistake("ChainId must be >=0, got ${ctx.chainID}")
        }
        val prevHeight = getLastBlockHeight(ctx)
        val prevTimestamp = getLastBlockTimestamp(ctx)
        val prevBlockRID = if (prevHeight == -1L) {
            blockchainRID.data
        } else {
            getBlockRID(ctx, prevHeight)
                    ?: throw ProgrammerMistake("Previous block had no RID. Check your block writing code!")
        }

        val blockIid = db.insertBlock(ctx, prevHeight + 1)
        return InitialBlockData(blockchainRID, blockIid, ctx.chainID, prevBlockRID, prevHeight + 1, prevTimestamp, blockHeightDependencies)
    }

    override fun addTransaction(bctx: BlockEContext, tx: Transaction, transactionNumber: Long): TxEContext {
        val txIid = DatabaseAccess.of(bctx).insertTransaction(bctx, tx, transactionNumber)
        return BaseTxEContext(bctx, txIid, tx)
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        DatabaseAccess.of(bctx).finalizeBlock(bctx, bh)
    }

    override fun commitBlock(bctx: BlockEContext, w: BlockWitness) {
        DatabaseAccess.of(bctx).commitBlock(bctx, w)
    }

    override fun getBlockHeightFromOwnBlockchain(ctx: EContext, blockRID: ByteArray): Long? {
        return DatabaseAccess.of(ctx).getBlockHeight(ctx, blockRID, ctx.chainID)
    }

    override fun getBlockHeightFromAnyBlockchain(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? {
        return DatabaseAccess.of(ctx).getBlockHeight(ctx, blockRID, chainId)
    }

    override fun getChainId(ctx: EContext, blockchainRID: BlockchainRid): Long? {
        return DatabaseAccess.of(ctx).getChainId(ctx, blockchainRID)
    }

    override fun getBlockRID(ctx: EContext, height: Long): ByteArray? {
        return DatabaseAccess.of(ctx).getBlockRID(ctx, height)
    }

    override fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray {
        return DatabaseAccess.of(ctx).getBlockHeader(ctx, blockRID)
    }

    // This implementation does not actually *stream* data from the database connection.
    // It is buffered in an ArrayList by ArrayListHandler() which is unfortunate.
    // Eventually, we may change this implementation to actually deliver a true
    // stream so that we don't have to store all transaction data in memory.
    override fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray> {
        return DatabaseAccess.of(ctx).getBlockTransactions(ctx, blockRID, false)
                .map { it.data as ByteArray }
    }

    override fun getTransactionInfo(ctx: EContext, txRID: ByteArray): TransactionInfoExt? {
        return DatabaseAccess.of(ctx).getTransactionInfo(ctx, txRID)
    }

    override fun getTransactionsInfo(ctx: EContext, beforeTime: Long, limit: Int): List<TransactionInfoExt> {
        return DatabaseAccess.of(ctx).getTransactionsInfo(ctx, beforeTime, limit)
    }

    override fun getLastTransactionNumber(ctx: EContext): Long {
        return DatabaseAccess.of(ctx).getLastTransactionNumber(ctx)
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return DatabaseAccess.of(ctx).getWitnessData(ctx, blockRID)
    }

    override fun getBlock(ctx: EContext, blockRID: ByteArray, txHashesOnly: Boolean): BlockDetail? {
        val db = DatabaseAccess.of(ctx)
        val blockInfo = db.getBlock(ctx, blockRID) ?: return null
        val txDetails = db.getBlockTransactions(ctx, blockInfo.blockRid, txHashesOnly)
        // TODO can I do this on the node or is it too computational expensive
        val headerRec = BlockHeaderData.fromBinary(blockInfo.blockHeader)
        return BlockDetail(
                blockInfo.blockRid,
                headerRec.getPreviousBlockRid(),
                blockInfo.blockHeader,
                blockInfo.blockHeight,
                txDetails,
                blockInfo.witness,
                blockInfo.timestamp)
    }

    override fun getBlocks(ctx: EContext, beforeTime: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> {
        val db = DatabaseAccess.of(ctx)
        val blocksInfo = db.getBlocks(ctx, beforeTime, limit)
        return blocksInfo.map { buildBlockDetail(it, db, ctx, txHashesOnly) }
    }

    override fun getBlocksBeforeHeight(ctx: EContext, beforeHeight: Long, limit: Int, txHashesOnly: Boolean): List<BlockDetail> {
        val db = DatabaseAccess.of(ctx)
        val blocksInfo = db.getBlocksBeforeHeight(ctx, beforeHeight, limit)
        return blocksInfo.map { buildBlockDetail(it, db, ctx, txHashesOnly) }
    }

    private fun buildBlockDetail(blockInfo: DatabaseAccess.BlockInfoExt, db: DatabaseAccess, ctx: EContext, txHashesOnly: Boolean): BlockDetail {
        val txs = db.getBlockTransactions(ctx, blockInfo.blockRid, txHashesOnly)

        // Decode block header
        val headerRec = BlockHeaderData.fromBinary(blockInfo.blockHeader)
        return BlockDetail(
                blockInfo.blockRid,
                headerRec.getPreviousBlockRid(),
                blockInfo.blockHeader,
                blockInfo.blockHeight,
                txs,
                blockInfo.witness,
                blockInfo.timestamp)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
    }

    override fun getBlockHeightInfo(ctx: EContext, blockchainRID: BlockchainRid): Pair<Long, Hash>? {
        return DatabaseAccess.of(ctx).getBlockHeightInfo(ctx, blockchainRID)
    }

    override fun getLastBlockTimestamp(ctx: EContext): Long {
        return DatabaseAccess.of(ctx).getLastBlockTimestamp(ctx)
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        return DatabaseAccess.of(ctx).getTxRIDsAtHeight(ctx, height)
    }

    override fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): ConfirmationProofMaterial? {
        val db = DatabaseAccess.of(ctx)
        return db.getBlockInfo(ctx, txRID)?.let { block ->
            ConfirmationProofMaterial(
                    WrappedByteArray(db.getTxHash(ctx, txRID)),
                    db.getBlockTxHashes(ctx, block.blockIid).map { WrappedByteArray(it) }.toTypedArray(),
                    block.blockHeader,
                    block.witness
            )
        }
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        return DatabaseAccess.of(ctx).getTxBytes(ctx, txRID)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        return DatabaseAccess.of(ctx).isTransactionConfirmed(ctx, txRID)
    }
}
