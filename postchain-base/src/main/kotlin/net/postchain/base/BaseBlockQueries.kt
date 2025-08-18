// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockDetailsTruncated
import net.postchain.core.block.BlockHeaderWithWitness
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockQueryHeightFilter
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.core.block.BlockStore
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.core.block.SimpleBlockHeader
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * A collection of methods for various blockchain-related queries. Each query is called with the wrapping method [runOp]
 * which will handle connections and logging.
 *
 * @param storage Connection manager
 * @param blockStore Blockchain storage facilitator
 * @param chainId Blockchain identifier
 * @param mySubjectId Public key related to the private key used for signing blocks
 */
abstract class BaseBlockQueries(
        private val storage: Storage,
        val blockStore: BlockStore,
        private val chainId: Long,
        private val mySubjectId: ByteArray,
        private val snapshotDatumRepository: SnapshotDatumRepository
) : BlockQueries {

    companion object : KLogging()

    @Volatile
    private var isShutdown: Boolean = false

    private var activeExecutions: Int = 0
    private val lock = ReentrantLock()
    private val shutdownComplete: Condition = lock.newCondition()

    protected fun <T> runOp(operation: (EContext) -> T): CompletionStage<T> {
        lock.withLock {
            if (isShutdown) return CompletableFuture.failedStage(PmEngineIsAlreadyClosed("Engine is closed", chainId))
            activeExecutions++
        }

        return try {
            runOpInternal(operation)
        } finally {
            lock.withLock {
                if (--activeExecutions == 0 && isShutdown) {
                    shutdownComplete.signalAll()
                }
            }
        }
    }

    private fun <T> runOpRegardless(operation: (EContext) -> T): CompletionStage<T> = runOpInternal(operation)

    private fun <T> runOpInternal(operation: (EContext) -> T): CompletionStage<T> {
        val ctx = try {
            storage.openReadConnection(chainId)
        } catch (e: SQLException) {
            if (isShutdown) return CompletableFuture.failedStage(PmEngineIsAlreadyClosed("Engine is closed", chainId, e))
            return CompletableFuture.failedStage(e)
        }

        val result = try {
            operation(ctx)
        } catch (e: Exception) {
            logger.trace(e) { "An error occurred" }
            return CompletableFuture.failedStage(e)
        } finally {
            storage.closeReadConnection(ctx)
        }

        return CompletableFuture.completedStage(result)
    }

    override fun getBlockSignature(blockRID: ByteArray): CompletionStage<Signature> = runOpRegardless { ctx ->
        val witnessData = blockStore.getWitnessData(ctx, blockRID)
        val witness = decodeWitness(witnessData)
        val signature = witness.getSignatures().find { it.subjectID.contentEquals(mySubjectId) }
        signature ?: throw UserMistake("Trying to get a signature from a node that doesn't have one")
    }

    override fun getLastBlockHeight(): CompletionStage<Long> = runOpRegardless {
        blockStore.getLastBlockHeight(it)
    }

    override fun getLastBlockTimestamp(): CompletionStage<Long> = runOpRegardless {
        blockStore.getLastBlockTimestamp(it)
    }

    /**
     * Retrieve the full list of transactions from the given block RID
     *
     * @param blockRID The block identifier
     * @throws ProgrammerMistake [blockRID] could not be found
     */
    override fun getBlockTransactionRids(blockRID: ByteArray): CompletionStage<List<ByteArray>> = runOpRegardless {
        // Shouldn't this be UserMistake?
        val height = blockStore.getBlockHeightFromOwnBlockchain(it, blockRID)
                ?: throw ProgrammerMistake("BlockRID does not exist")
        blockStore.getTxRIDsAtHeight(it, height).toList()
    }

    override fun getTransaction(txRID: ByteArray): CompletionStage<Transaction?> =
            CompletableFuture.failedStage(UserMistake("getTransaction is not supported"))

    override fun getTransactionRawData(txRID: ByteArray): CompletionStage<ByteArray?> = runOpRegardless {
        blockStore.getTxBytes(it, txRID)
    }

    override fun getTransactionInfo(txRID: ByteArray, includeTxData: Boolean): CompletionStage<TransactionInfoExt?> = runOpRegardless {
        blockStore.getTransactionInfo(it, txRID, includeTxData)
    }

    override fun getTransactionsInfo(timeFilter: BlockQueryTimeFilter, limit: Int, maxDataSize: Int): CompletionStage<TransactionInfoExtsTruncated> =
            runOpRegardless {
                blockStore.getTransactionsInfo(it, timeFilter, limit, maxDataSize)
            }

    override fun getTransactionsInfoBySigner(timeFilter: BlockQueryTimeFilter, limit: Int, signer: PubKey, maxDataSize: Int): CompletionStage<TransactionInfoExtsTruncated> =
            runOpRegardless {
                blockStore.getTransactionsInfoBySigner(it, timeFilter, limit, signer, maxDataSize)
            }

    override fun getLastTransactionNumber(): CompletionStage<Long> = runOpRegardless {
        blockStore.getLastTransactionNumber(it)
    }

    override fun getBlocksBetweenTimes(timeFilter: BlockQueryTimeFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): CompletionStage<BlockDetailsTruncated> =
            runOpRegardless {
                blockStore.getBlocksBetweenTimes(it, timeFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)
            }

    override fun getBlocksBetweenHeights(heightFilter: BlockQueryHeightFilter, limit: Int, txHashesOnly: Boolean, maxDataSize: Int, excludeEmpty: Boolean): CompletionStage<BlockDetailsTruncated> =
            runOpRegardless {
                blockStore.getBlocksBetweenHeights(it, heightFilter, limit, txHashesOnly, maxDataSize, excludeEmpty)
            }

    override fun getBlocksFromHeight(fromHeight: Long, limit: Int): CompletionStage<List<DatabaseAccess.BlockInfoExt>> =
        runOpRegardless {
            blockStore.getBlocksFromHeight(it, fromHeight, limit)
        }

    override fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): CompletionStage<BlockDetail?> =
            runOpRegardless {
                blockStore.getBlock(it, blockRID, txHashesOnly)
            }

    override fun getBlockRid(height: Long): CompletionStage<ByteArray?> = runOpRegardless {
        blockStore.getBlockRID(it, height)
    }

    override fun isTransactionConfirmed(txRID: ByteArray): CompletionStage<Boolean> = runOpRegardless {
        blockStore.isTransactionConfirmed(it, txRID)
    }

    override fun getConfirmationProof(txRID: ByteArray): CompletionStage<ConfirmationProof?> = runOpRegardless { ctx ->
        blockStore.getConfirmationProofMaterial(ctx, txRID)?.let { material ->
            val decodedWitness = decodeWitness(material.witness)

            val version = blockStore.getMerkleHashVersion(ctx, BlockHeaderData.fromBinary(material.header).getHeight())
            val result = merkleProofTree(material.txHash, material.txHashes, makeMerkleHashCalculator(version))
            val txIndex = result.first
            val merkleProofTree = result.second
            ConfirmationProof(
                    material.txHash.data,
                    material.header,
                    decodedWitness as BaseBlockWitness,
                    merkleProofTree,
                    txIndex
            )
        }
    }

    /**
     * Retrieve the full block at a specified height by first retrieving the wanted block RID and then
     * getting each element of that block that will allow us to build the full block.
     *
     * If includeTransactions is false (default = true), no transaction data will be included
     * in the result, which means that only the header+witness is returned.
     *
     * @throws UserMistake No block could be found at the specified height
     * @throws ProgrammerMistake Too many blocks (>1) found at the specified height
     */
    override fun getBlockAtHeight(height: Long, includeTransactions: Boolean): CompletionStage<BlockDataWithWitness?> =
            runOpRegardless {
                val blockRID = blockStore.getBlockRID(it, height)
                if (blockRID == null) {
                    null
                } else {
                    val headerBytes = blockStore.getBlockHeader(it, blockRID)
                    val witnessBytes = blockStore.getWitnessData(it, blockRID)
                    val txBytes = if (includeTransactions) blockStore.getBlockTransactions(it, blockRID) else listOf()
                    val header = SimpleBlockHeader(
                            prevBlockRID = BlockHeaderData.fromBinary(headerBytes).getPreviousBlockRid(),
                            rawData = headerBytes,
                            blockRID = blockRID)
                    val witness = decodeWitness(witnessBytes)

                    BlockDataWithWitness(header, txBytes, witness)
                }
            }

    override fun getLatestSnapshotBlockHeader(): CompletionStage<BlockHeaderWithWitness?> = runOpRegardless {
        snapshotDatumRepository.getLatestSnapshotHeight(it)?.let { latestSnapshotHeight ->
            val blockRID = blockStore.getBlockRID(it, latestSnapshotHeight)
                    ?: throw ProgrammerMistake("Latest snapshot height $latestSnapshotHeight is not found in the blockchain")
            val headerBytes = blockStore.getBlockHeader(it, blockRID)
            val witnessBytes = blockStore.getWitnessData(it, blockRID)
            val header = SimpleBlockHeader(
                    prevBlockRID = BlockHeaderData.fromBinary(headerBytes).getPreviousBlockRid(),
                    rawData = headerBytes,
                    blockRID = blockRID)
            val witness = decodeWitness(witnessBytes)

            BlockHeaderWithWitness(header, witness)
        }
    }

    override fun getSnapshotContextMaxIds(height: Long): CompletionStage<Map<Long, Long?>> = runOpRegardless {
        snapshotDatumRepository.getContextMaxIds(it, height)
    }

    override fun getSnapshotData(height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long):
            CompletionStage<List<Triple<Long, Gtv, Boolean>>> = runOpRegardless {
        snapshotDatumRepository.getDatumsBySize(it, height, contextId, datumIdFrom, maxDataSize)
    }

    override fun shutdown() {
        logger.debug { "Shutting down block queries" }
        lock.withLock {
            isShutdown = true
            if (activeExecutions > 0) {
                logger.debug { "Waiting for $activeExecutions queries to complete..." }
                if (!shutdownComplete.await(2, TimeUnit.SECONDS)) {
                    logger.warn("Waiting for block query shutdown timed out. Shutting down with $activeExecutions non-completed queries.")
                }
            }
        }
        logger.debug { "Block queries shutdown complete" }
    }

    protected abstract fun decodeBlockHeader(headerData: ByteArray): BaseBlockHeader

    protected abstract fun decodeWitness(witnessData: ByteArray): MultiSigBlockWitness
}
