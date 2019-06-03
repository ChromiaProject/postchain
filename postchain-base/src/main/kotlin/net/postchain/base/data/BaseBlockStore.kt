// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.merkle.Hash
import net.postchain.common.toHex
import net.postchain.core.*

/**
 * Provides database access to the location where the blockchain with related metadata and transactions
 * are stored
 *
 * @property db Object used to access the DBMS
 */
class BaseBlockStore : BlockStore {

    companion object: KLogging()


    /**
     * Get initial block data, i.e. data necessary for building the next block
     *
     * We write the block skeleton (since we want the block_iid) and the blocks we are depending on
     *  (since no point in waiting) to DB at this point.
     *
     * @param ctx Connection context
     * @returns Initial block data
     */
    override fun beginBlock(ctx: EContext, blockchainDependencies: BlockchainDependencies): InitialBlockData {
        val db = DatabaseAccess.of(ctx)
        if (ctx.chainID < 0) {
            throw UserMistake("ChainId must be >=0, got ${ctx.chainID}")
        }

        val prevHeight = getLastBlockHeight(ctx)
        val prevTimestamp = getLastBlockTimestamp(ctx)
        val blockchainRID: ByteArray = db.getBlockchainRID(ctx)
                    ?: throw UserMistake("Blockchain RID not found for chainId ${ctx.chainID}")
        val prevBlockRID = if (prevHeight == -1L) {
            blockchainRID
        } else {
            getBlockRID(ctx, prevHeight) ?: throw ProgrammerMistake("Previous block had no RID. Check your block writing code!")
        }

        val blockIid = db.insertBlock(ctx, prevHeight + 1)
        addBlockchainDependencies(ctx, blockIid, blockchainDependencies)

        return InitialBlockData(
                blockchainRID,
                blockIid,
                ctx.chainID,
                prevBlockRID,
                prevHeight + 1,
                prevTimestamp,
                blockchainDependencies.extractBlockHeightDependencyArray()
        )
    }

    override fun addTransaction(bctx: BlockEContext, tx: Transaction): TxEContext {
        val txIid = DatabaseAccess.of(bctx).insertTransaction(bctx, tx)
        return BaseTxEContext(bctx, txIid)
    }

    override fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader) {
        DatabaseAccess.of(bctx).finalizeBlock(bctx, bh)
    }


    override fun commitBlock(bctx: BlockEContext, w: BlockWitness?) {
        if (w == null) return
        DatabaseAccess.of(bctx).commitBlock(bctx, w)
    }

    override fun getBlockHeightFromOwnBlockchain(ctx: EContext, blockRID: ByteArray): Long? {
        return DatabaseAccess.of(ctx).getBlockHeight(ctx, blockRID, ctx.chainID)
    }

    override fun getBlockHeightFromAnyBlockchain(ctx: EContext, blockRID: ByteArray, chainId: Long): Long? {
        return DatabaseAccess.of(ctx).getBlockHeight(ctx, blockRID, chainId)
    }

    override fun getChainId(ctx: EContext, blockchainRID: ByteArray): Long? {
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
        return DatabaseAccess.of(ctx).getBlockTransactions(ctx, blockRID)
    }

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return DatabaseAccess.of(ctx).getWitnessData(ctx, blockRID)
    }

    override fun getLastBlockHeight(ctx: EContext): Long {
        return DatabaseAccess.of(ctx).getLastBlockHeight(ctx)
    }

    override fun getBlockHeightInfo(ctx: EContext, blockchainRID: ByteArray): Pair<Long, Hash>? {
        return DatabaseAccess.of(ctx).getBlockHeightInfo(ctx, blockchainRID)
    }

    /**
     * @return all dependencies belonging to the last/previous block of this chain
     */
    override fun getLastBlockDependencies(ctx: EContext): BlockchainDependencies {
        val db = DatabaseAccess.of(ctx)
        val lastBlock = db.getLastBlockRid(ctx, ctx.chainID)
        return if (lastBlock == null) {
            BlockchainDependencies(listOf()) // This might be our first block
        } else {
            db.getDependencyBlockHeights(ctx, lastBlock)
        }
    }


    /**
     * Writes the dependencies from [ourBlockIid] to [blockDeps] to the DB.
     *
     * Note: We are assuming that checks has been done and data is correct.
     *
     * @param ctx holds the current chain id
     * @param ourBlockIid the new block IID we are going to build
     * @param blockDeps holds the dependencies we are to write to DB
     */
    override fun addBlockchainDependencies(ctx: EContext, ourBlockIid: Long, blockDeps: BlockchainDependencies) {
        val db = DatabaseAccess.of(ctx)

        for (newDep in blockDeps.all()) {
            val depChainId: Long  = newDep.blockchainRelatedInfo.chainId!!
            val depBlockRid  = newDep.heightDependency!!.lastBlockRid
            if (logger.isDebugEnabled) {
                logger.debug { "Writing dependency for our block id: $ourBlockIid to dep block RID: ${depBlockRid.toHex()} " +
                        "(dep chain id: $depChainId of height: ${newDep.heightDependency!!.height})" }
            }
            db.addBlockDependency(ctx, ourBlockIid, depBlockRid)
            /*
            } else {
                throw ProgrammerMistake("We are not allowed to decrease dependency (${ctx.chainID} -> $depChainId) block height from $oldHeight to $newBlockHeight ")
            }
            */
        }

    }

    override fun getLastBlockTimestamp(ctx: EContext): Long {
        return DatabaseAccess.of(ctx).getLastBlockTimestamp(ctx)
    }

    override fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray> {
        return DatabaseAccess.of(ctx).getTxRIDsAtHeight(ctx, height)
    }

    override fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): Any {
        val db = DatabaseAccess.of(ctx)
        val block = db.getBlockInfo(ctx, txRID)
        return ConfirmationProofMaterial(
                ByteArrayKey(db.getTxHash(ctx, txRID)),
                db.getBlockTxHashes(ctx, block.blockIid).map{ ByteArrayKey(it) }.toTypedArray(),
                block.blockHeader,
                block.witness
        )
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        return DatabaseAccess.of(ctx).getTxBytes(ctx, txRID)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        return DatabaseAccess.of(ctx).isTransactionConfirmed(ctx, txRID)
    }

    fun initialize(ctx: EContext, blockchainRID: ByteArray, dependencies:  List<BlockchainRelatedInfo>) {
        DatabaseAccess.of(ctx).checkBlockchainRID(ctx, blockchainRID)

        // Verify all dependencies
        for (dep in dependencies) {
            val chainId = DatabaseAccess.of(ctx).getChainId(ctx, dep.blockchainRid)
            if (chainId == null) {
                throw BadDataMistake(BadDataType.BAD_CONFIGURATION,
                        "Dependency given in configuration: ${dep.nickname} is missing in DB. Dependent blockchains must be added in correct order!")
            } else {
                logger.info("initialize() - Verified BC dependency: ${dep.nickname} exists as chainID: = $chainId (before: ${dep.chainId}) ")
                dep.chainId = chainId
            }
        }
    }
}
