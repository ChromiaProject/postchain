// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.gtv.RowData
import net.postchain.base.merkle.Hash
import net.postchain.core.*

/**
 * Provides database access to the location where the blockchain with related metadata and transactions
 * are stored
 *
 * @property db Object used to access the DBMS
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

    override fun commitSnapshot(ctx: EContext, rootHash: ByteArray, height: Long) {
        val db = DatabaseAccess.of(ctx)
        val snapshotIid = db.insertSnapshot(ctx, rootHash, height)
        println(snapshotIid)
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

    override fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray {
        return DatabaseAccess.of(ctx).getWitnessData(ctx, blockRID)
    }

    override fun getBlock(ctx: EContext, blockRID: ByteArray, hashesOnly: Boolean): BlockDetail? {
        val db = DatabaseAccess.of(ctx)
        val blockInfo = db.getBlock(ctx, blockRID) ?: return null
        var txDetails = listOf<TxDetail>()
        txDetails = db.getBlockTransactions(ctx, blockInfo.blockRid, hashesOnly)
        val blockHeaderDecoded = BaseBlockHeader(blockInfo.blockHeader, SECP256K1CryptoSystem()) // TODO can I do this on the node or is it too computational expensive
        return BlockDetail(blockInfo.blockRid, blockHeaderDecoded.prevBlockRID, blockInfo.blockHeader, blockInfo.blockHeight, txDetails, blockInfo.witness, blockInfo.timestamp)
    }

    override fun getBlocks(ctx: EContext, blockTime: Long, limit: Int, hashesOnly: Boolean): List<BlockDetail> {
        val db = DatabaseAccess.of(ctx)
        val blocksInfo = db.getBlocks(ctx, blockTime, limit)
        return blocksInfo.map { blockInfo ->
            val txs = db.getBlockTransactions(ctx, blockInfo.blockRid, hashesOnly)

            // Decode block header
            val blockHeaderDecoded = BaseBlockHeader(blockInfo.blockHeader, SECP256K1CryptoSystem())

            BlockDetail(
                    blockInfo.blockRid,
                    blockHeaderDecoded.prevBlockRID,
                    blockInfo.blockHeader,
                    blockInfo.blockHeight,
                    txs,
                    blockInfo.witness,
                    blockInfo.timestamp)
        }
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

    override fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): Any {
        val db = DatabaseAccess.of(ctx)
        val block = db.getBlockInfo(ctx, txRID)
        return ConfirmationProofMaterial(
                ByteArrayKey(db.getTxHash(ctx, txRID)),
                db.getBlockTxHashes(ctx, block.blockIid).map { ByteArrayKey(it) }.toTypedArray(),
                block.blockHeader,
                block.witness
        )
    }

    override fun getChunkData(ctx: EContext, limit: Int, offset: Long): List<RowData> {
        val db = DatabaseAccess.of(ctx)
        val data = mutableListOf<RowData>()
        var original = 0L
        var tables = getTables(ctx)
        while (data.size < limit && tables.isNotEmpty()) {
            val tableName = tables.first()
            val rows = when(tableName) {
                "meta" -> db.getMetaInRange(ctx, limit-data.size, offset+data.size, original)
                "blockchains" -> db.getBlockchainsInRange(ctx, limit-data.size, offset+data.size, original)
                "blocks" -> db.getBlocksInRange(ctx, limit-data.size, offset+data.size, original)
                "transactions" -> db.getTxsInRange(ctx, limit-data.size, offset+data.size, original)
                "configurations" -> db.getConfigurationsInRange(ctx, limit-data.size, offset+data.size, original)
                "peerinfos" -> db.getPeerInfosInRange(ctx, limit-data.size, offset+data.size, original)
                else -> db.getDataInRange(ctx, tableName, limit-data.size, offset+data.size, original)
            }
            if (rows.isEmpty()) {
                tables = tables.drop(1)
                original += db.getRowCount(ctx, tableName)
                continue
            }
            data.addAll(rows)
            tables = tables.drop(1)
            original += db.getRowCount(ctx, tableName)
        }
        return data.sorted()
    }

    override fun getTxBytes(ctx: EContext, txRID: ByteArray): ByteArray? {
        return DatabaseAccess.of(ctx).getTxBytes(ctx, txRID)
    }

    override fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean {
        return DatabaseAccess.of(ctx).isTransactionConfirmed(ctx, txRID)
    }

    fun initialValidation(ctx: EContext, dependencies: List<BlockchainRelatedInfo>) {
        // At this point we must have stored BC RID
        DatabaseAccess.of(ctx).getBlockchainRID(ctx)
                ?: throw IllegalStateException("Cannot initialize block store for a chain without a RID")

        // Verify all dependencies
        for (dep in dependencies) {
            logger.debug("Validating")
            val chainId = DatabaseAccess.of(ctx).getChainId(ctx, dep.blockchainRid)
            if (chainId == null) {
                throw BadDataMistake(BadDataType.BAD_CONFIGURATION,
                        "Dependency given in configuration: ${dep.nickname} is missing in DB. Dependent blockchains must be added in correct order!" +
                                " Dependency not found BC RID ${dep.blockchainRid.toHex()}")
            } else {
                logger.info("initialize() - Verified BC dependency: ${dep.nickname} exists as chainID: = $chainId (before: ${dep.chainId}) ")
                dep.chainId = chainId
            }
        }
    }

    // Get list of tables
    private fun getTables(ctx: EContext): List<String> {
//        val preDefined = mutableListOf("meta", "blockchains", "blocks", "transactions", "configurations", "peerinfos")
        return DatabaseAccess.of(ctx).getTables(ctx)
//        var rellAppTables = mutableListOf<String>()
//        tables.forEach {
//            if (!preDefined.contains(it)) {
//                rellAppTables.add(it)
//            }
//        }
//        return rellAppTables
    }
}
