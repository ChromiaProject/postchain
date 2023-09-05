// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.PmEngineIsAlreadyClosed
import net.postchain.core.Storage
import net.postchain.core.Transaction
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockStore
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.crypto.Digester
import net.postchain.crypto.PubKey
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Encapsulating a proof of a transaction hash in a block header
 *
 * @param hash The transaction hash the proof applies to
 * @param blockHeader The block header the [hash] is supposedly in
 * @param witness The block witness
 * @param merkleProofTree a proof including [hash] (in its raw form)
 * @param txIndex is the index of the proven transaction in the block (i.e. our "path").
 */
class ConfirmationProof(
        @Name("hash") val hash: ByteArray,
        @Name("blockHeader") val blockHeader: ByteArray,
        @Name("witness") val witness: BaseBlockWitness,
        @Name("merkleProofTree") val merkleProofTree: GtvMerkleProofTree,
        @Name("txIndex") val txIndex: Long
)

/**
 * A collection of methods for various blockchain-related queries. Each query is called with the wrapping method [runOp]
 * which will handle connections and logging.
 *
 * @param digester Digester
 * @param storage Connection manager
 * @param blockStore Blockchain storage facilitator
 * @param chainId Blockchain identifier
 * @param mySubjectId Public key related to the private key used for signing blocks
 */
open class BaseBlockQueries(
        private val digester: Digester,
        private val storage: Storage,
        val blockStore: BlockStore,
        private val chainId: Long,
        private val mySubjectId: ByteArray
) : BlockQueries {

    companion object : KLogging()

    // Create a separate Executor service to make sure other tasks do not compete with BlockQueries
    private val workerPool: ExecutorService = Executors.newFixedThreadPool(storage.readConcurrency, object : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(runnable: Runnable): Thread = Thread(null, runnable, "${counter.incrementAndGet()}-BlockQueries")
    })

    /**
     * Wrapper function for a supplied function with the goal of opening a new read-only connection, catching any exceptions
     * on the query being run and logging them, and finally closing the connection
     */
    protected fun <T> runOp(operation: (EContext) -> T): CompletionStage<T> {
        if (workerPool.isShutdown) return CompletableFuture.failedStage(PmEngineIsAlreadyClosed("Engine is closed"))
        return CompletableFuture.supplyAsync({
            val ctx = try {
                storage.openReadConnection(chainId)
            } catch (e: SQLException) {
                if (workerPool.isShutdown) throw PmEngineIsAlreadyClosed("Engine is closed", e)
                throw e
            }

            try {
                operation(ctx)
            } catch (e: Exception) {
                logger.trace(e) { "An error occurred" }
                throw e
            } finally {
                storage.closeReadConnection(ctx)
            }
        }, workerPool)
    }

    override fun getBlockSignature(blockRID: ByteArray): CompletionStage<Signature> {
        return runOp { ctx ->
            val witnessData = blockStore.getWitnessData(ctx, blockRID)
            val witness = decodeWitness(witnessData)
            val signature = witness.getSignatures().find { it.subjectID.contentEquals(mySubjectId) }
            signature ?: throw UserMistake("Trying to get a signature from a node that doesn't have one")
        }
    }

    override fun getLastBlockHeight(): CompletionStage<Long> {
        return runOp {
            blockStore.getLastBlockHeight(it)
        }
    }

    override fun getLastBlockTimestamp(): CompletionStage<Long> {
        return runOp {
            blockStore.getLastBlockTimestamp(it)
        }
    }

    /**
     * Retrieve the full list of transactions from the given block RID
     *
     * @param blockRID The block identifier
     * @throws ProgrammerMistake [blockRID] could not be found
     */
    override fun getBlockTransactionRids(blockRID: ByteArray): CompletionStage<List<ByteArray>> {
        return runOp {
            // Shouldn't this be UserMistake?
            val height = blockStore.getBlockHeightFromOwnBlockchain(it, blockRID)
                    ?: throw ProgrammerMistake("BlockRID does not exist")
            blockStore.getTxRIDsAtHeight(it, height).toList()
        }
    }

    override fun getTransaction(txRID: ByteArray): CompletionStage<Transaction?> {
        return CompletableFuture.failedFuture(UserMistake("getTransaction is not supported"))
    }

    override fun getTransactionRawData(txRID: ByteArray): CompletionStage<ByteArray?> {
        return runOp {
            blockStore.getTxBytes(it, txRID)
        }
    }

    override fun getTransactionInfo(txRID: ByteArray): CompletionStage<TransactionInfoExt?> {
        return runOp {
            blockStore.getTransactionInfo(it, txRID)
        }
    }

    override fun getTransactionsInfo(beforeTime: Long, limit: Int): CompletionStage<List<TransactionInfoExt>> {
        return runOp {
            blockStore.getTransactionsInfo(it, beforeTime, limit)
        }
    }

    override fun getTransactionsInfoBySigner(beforeTime: Long, limit: Int, signer: PubKey): CompletionStage<List<TransactionInfoExt>> {
        return runOp {
            blockStore.getTransactionsInfoBySigner(it, beforeTime, limit, signer)
        }
    }

    override fun getLastTransactionNumber(): CompletionStage<Long> {
        return runOp {
            blockStore.getLastTransactionNumber(it)
        }
    }

    override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>> {
        return runOp {
            blockStore.getBlocks(it, beforeTime, limit, txHashesOnly)
        }
    }

    override fun getBlocksBeforeHeight(beforeHeight: Long, limit: Int, txHashesOnly: Boolean): CompletionStage<List<BlockDetail>> {
        return runOp {
            blockStore.getBlocksBeforeHeight(it, beforeHeight, limit, txHashesOnly)
        }
    }

    override fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean): CompletionStage<BlockDetail?> {
        return runOp {
            blockStore.getBlock(it, blockRID, txHashesOnly)
        }
    }

    override fun getBlockRid(height: Long): CompletionStage<ByteArray?> {
        return runOp {
            blockStore.getBlockRID(it, height)
        }
    }

    override fun isTransactionConfirmed(txRID: ByteArray): CompletionStage<Boolean> {
        return runOp {
            blockStore.isTransactionConfirmed(it, txRID)
        }
    }

    override fun shutdown() {
        workerPool.shutdown()
    }

    override fun query(name: String, args: Gtv): CompletionStage<Gtv> {
        return CompletableFuture.failedFuture(UserMistake("Queries are not supported"))
    }

    override fun getConfirmationProof(txRID: ByteArray): CompletionStage<ConfirmationProof?> {
        return runOp {
            blockStore.getConfirmationProofMaterial(it, txRID)?.let { material ->
                val decodedWitness = decodeWitness(material.witness)
                val decodedBlockHeader = decodeBlockHeader(material.header)

                val result = decodedBlockHeader.merkleProofTree(material.txHash, material.txHashes)
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
    }

    override fun getBlockHeader(blockRID: ByteArray): CompletionStage<BlockHeader> {
        return runOp {
            val headerBytes = blockStore.getBlockHeader(it, blockRID)
            decodeBlockHeader(headerBytes)
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
    override fun getBlockAtHeight(height: Long, includeTransactions: Boolean): CompletionStage<BlockDataWithWitness?> {
        return runOp {
            val blockRID = blockStore.getBlockRID(it, height)
            if (blockRID == null) {
                null
            } else {
                val headerBytes = blockStore.getBlockHeader(it, blockRID)
                val witnessBytes = blockStore.getWitnessData(it, blockRID)
                val txBytes = if (includeTransactions) blockStore.getBlockTransactions(it, blockRID) else listOf()
                val header = decodeBlockHeader(headerBytes)
                val witness = decodeWitness(witnessBytes)

                BlockDataWithWitness(header, txBytes, witness)
            }
        }
    }

    protected open fun decodeBlockHeader(headerData: ByteArray): BaseBlockHeader =
            BaseBlockHeader(headerData, GtvMerkleHashCalculator(digester))

    protected open fun decodeWitness(witnessData: ByteArray): MultiSigBlockWitness =
            BaseBlockWitness.fromBytes(witnessData)
}
