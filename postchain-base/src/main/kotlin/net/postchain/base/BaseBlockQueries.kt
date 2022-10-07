// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import mu.KLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.*
import net.postchain.core.block.*
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.utils.KovenantHelper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

/**
 * Encapsulating a proof of a transaction hash in a block header
 *
 * @param txHash The transaction hash the proof applies to
 * @param header The block header the [txHash] is supposedly in
 * @param witness The block witness
 * @param proof a proof including [txHash] (in its raw form)
 */
class ConfirmationProof(val txHash: ByteArray, val header: ByteArray, val witness: BlockWitness, val proof: GtvMerkleProofTree)

/**
 * A collection of methods for various blockchain-related queries. Each query is called with the wrapping method [runOp]
 * which will handle connections and logging.
 *
 * @param blockchainConfiguration Configuration data for the blockchain
 * @param storage Connection manager
 * @param blockStore Blockchain storage facilitator
 * @param chainId Blockchain identifier
 * @param mySubjectId Public key related to the private key used for signing blocks
 */
open class BaseBlockQueries(
        private val blockchainConfiguration: BlockchainConfiguration,
        private val storage: Storage,
        private val blockStore: BlockStore,
        private val chainId: Long,
        private val mySubjectId: ByteArray
) : BlockQueries {

    companion object : KLogging()

    // Create a separate Kovenant context to make sure other tasks do not compete with BlockQueries
    val kctx = KovenantHelper.createContext("BlockQueries", storage.readConcurrency)

    /**
     * Wrapper function for a supplied function with the goal of opening a new read-only connection, catching any exceptions
     * on the query being run and logging them, and finally closing the connection
     */
    protected fun <T> runOp(chainId: Long?, operation: (EContext) -> T): Promise<T, Exception> {
        return task(kctx) {
            val ctx = storage.openReadConnection(chainId ?: this.chainId)
            try {
                operation(ctx)
            } catch (e: Exception) {
                logger.trace(e) { "An error occurred" }
                throw e
            } finally {
                storage.closeReadConnection(ctx)
            }
        }
    }

    override fun getBlockSignature(blockRID: ByteArray, chainId: Long?): Promise<Signature, Exception> {
        return runOp(chainId) { ctx ->
            val witnessData = blockStore.getWitnessData(ctx, blockRID)
            val witness = blockchainConfiguration.decodeWitness(witnessData) as MultiSigBlockWitness
            val signature = witness.getSignatures().find { it.subjectID.contentEquals(mySubjectId) }
            signature ?: throw UserMistake("Trying to get a signature from a node that doesn't have one")
        }
    }

    override fun getBestHeight(chainId: Long?): Promise<Long, Exception> {
        return runOp(chainId) {
            blockStore.getLastBlockHeight(it)
        }
    }

    override fun getLastBlockTimestamp(chainId: Long?): Promise<Long, Exception> {
        return runOp(chainId) {
            blockStore.getLastBlockTimestamp(it)
        }
    }

    /**
     * Retrieve the full list of transactions from the given block RID
     *
     * @param blockRID The block identifier
     * @throws ProgrammerMistake [blockRID] could not be found
     */
    override fun getBlockTransactionRids(blockRID: ByteArray, chainId: Long?): Promise<List<ByteArray>, Exception> {
        return runOp(chainId) {
            // Shouldn't this be UserMistake?
            val height = blockStore.getBlockHeightFromOwnBlockchain(it, blockRID)
                    ?: throw ProgrammerMistake("BlockRID does not exist")
            blockStore.getTxRIDsAtHeight(it, height).toList()
        }
    }

    override fun getTransaction(txRID: ByteArray, chainId: Long?): Promise<Transaction?, Exception> {
        return runOp(chainId) {
            val txBytes = blockStore.getTxBytes(it, txRID)
            if (txBytes == null)
                null
            else
                blockchainConfiguration.getTransactionFactory().decodeTransaction(txBytes)
        }
    }

    override fun getTransactionInfo(txRID: ByteArray, chainId: Long?): Promise<TransactionInfoExt?, Exception> {
        return runOp(chainId) {
            blockStore.getTransactionInfo(it, txRID)
        }
    }

    override fun getTransactionsInfo(beforeTime: Long, limit: Int, chainId: Long?): Promise<List<TransactionInfoExt>, Exception> {
        return runOp(chainId) {
            blockStore.getTransactionsInfo(it, beforeTime, limit)
        }
    }

    override fun getBlocks(beforeTime: Long, limit: Int, txHashesOnly: Boolean, chainId: Long?): Promise<List<BlockDetail>, Exception> {
        return runOp(chainId) {
            blockStore.getBlocks(it, beforeTime, limit, txHashesOnly)
        }
    }

    override fun getBlock(blockRID: ByteArray, txHashesOnly: Boolean, chainId: Long?): Promise<BlockDetail?, Exception> {
        return runOp(chainId) {
            blockStore.getBlock(it, blockRID, txHashesOnly)
        }
    }

    override fun getBlockRid(height: Long, chainId: Long?): Promise<ByteArray?, Exception> {
        return runOp(chainId) {
            blockStore.getBlockRID(it, height)
        }
    }

    override fun isTransactionConfirmed(txRID: ByteArray, chainId: Long?): Promise<Boolean, Exception> {
        return runOp(chainId) {
            blockStore.isTransactionConfirmed(it, txRID)
        }
    }

    override fun query(query: String, chainId: Long?): Promise<String, Exception> {
        return Promise.ofFail(UserMistake("Queries are not supported"))
    }

    override fun query(name: String, args: Gtv, chainId: Long?): Promise<Gtv, Exception> {
        return Promise.ofFail(UserMistake("Queries are not supported"))
    }

    fun getConfirmationProof(txRID: ByteArray): Promise<ConfirmationProof?, Exception> {
        return runOp(chainId) {
            val material = blockStore.getConfirmationProofMaterial(it, txRID) as ConfirmationProofMaterial
            val decodedWitness = blockchainConfiguration.decodeWitness(material.witness)
            val decodedBlockHeader = blockchainConfiguration.decodeBlockHeader(material.header) as BaseBlockHeader

            val merkleProofTree = decodedBlockHeader.merklePath(material.txHash, material.txHashes)
            ConfirmationProof(material.txHash.byteArray, material.header, decodedWitness, merkleProofTree)
        }
    }

    override fun getBlockHeader(blockRID: ByteArray, chainId: Long?): Promise<BlockHeader, Exception> {
        return runOp(chainId) {
            val headerBytes = blockStore.getBlockHeader(it, blockRID)
            blockchainConfiguration.decodeBlockHeader(headerBytes)
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
    override fun getBlockAtHeight(height: Long, includeTransactions: Boolean, chainId: Long?): Promise<BlockDataWithWitness?, Exception> {
        return runOp(chainId) {
            val blockRID = blockStore.getBlockRID(it, height)
            if (blockRID == null) {
                null
            } else {
                val headerBytes = blockStore.getBlockHeader(it, blockRID)
                val witnessBytes = blockStore.getWitnessData(it, blockRID)
                val txBytes = if (includeTransactions) blockStore.getBlockTransactions(it, blockRID) else listOf()
                val header = blockchainConfiguration.decodeBlockHeader(headerBytes)
                val witness = blockchainConfiguration.decodeWitness(witnessBytes)

                BlockDataWithWitness(header, txBytes, witness)
            }
        }
    }
}
