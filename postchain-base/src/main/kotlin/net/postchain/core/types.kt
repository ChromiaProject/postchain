// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.core

// TODO: remove core's dependency on base
import net.postchain.base.Storage
import nl.komponents.kovenant.Promise
import org.apache.commons.configuration2.Configuration
import java.sql.Connection
import java.util.*

/*
1. Manager reads JSON and finds BlockchainConfigurationFactory class name.
2. Manager instantiates a class which implements BlockchainConfigurationFactory interface, and feeds it JSON data.
3. BlockchainConfigurationFactory creates BlockchainConfiguration object.
4. BlockchainConfiguration acts as a block factory and creates a transaction factory, presumably passing it configuration data in some form.
5. TransactionFactory will create Transaction objects according to configuration, possibly also passing it the configuration data.
6. Transaction object can perform its duties according to the configuration it received, perhaps creating sub-objects called transactors and passing them the configuration.
 */
// TODO: can we generalize conn? We can make it an Object, but then we have to do typecast everywhere...
open class EContext(val conn: Connection, val chainID: Long, val nodeID: Int)

open class BlockEContext(conn: Connection, chainID: Long, nodeID: Int, val blockIID: Long, val timestamp: Long)
    : EContext(conn, chainID, nodeID)

class TxEContext(conn: Connection, chainID: Long, nodeID: Int, blockIID: Long, timestamp: Long, val txIID: Long)
    : BlockEContext(conn, chainID, nodeID, blockIID, timestamp)

interface BlockHeader {
    val prevBlockRID: ByteArray
    val rawData: ByteArray
    val blockRID: ByteArray // it's not a part of header but derived from it
}

open class BlockData(val header: BlockHeader, val transactions: List<ByteArray>)

// Witness is a generalization over signatures
// Block-level witness is something which proves that block is valid and properly authorized

interface BlockWitness {
    //    val blockRID: ByteArray
    fun getRawData(): ByteArray
}

interface WitnessPart

open class BlockDataWithWitness(header: BlockHeader, transactions: List<ByteArray>, val witness: BlockWitness?)
    : BlockData(header, transactions)

// id is something which identifies subject which produces the
// signature, e.g. pubkey or hash of pubkey
data class Signature(val subjectID: ByteArray, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        if (!Arrays.equals(subjectID, other.subjectID)) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(subjectID)
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}

interface MultiSigBlockWitness : BlockWitness {
    fun getSignatures(): Array<Signature>
}

interface BlockWitnessBuilder {
    fun isComplete(): Boolean
    fun getWitness(): BlockWitness // throws when not complete
}

interface MultiSigBlockWitnessBuilder : BlockWitnessBuilder {
    fun getMySignature(): Signature
    fun applySignature(s: Signature)
}

/**
 * Transactor is an individual operation which can be applied to the database
 * Transaction might consist of one or more operations
 * Transaction should be serializable, but transactor doesn't need to have a serialized
 * representation as we only care about storing of the whole Transaction
 */
interface Transactor {
    fun isCorrect(): Boolean
    fun apply(ctx: TxEContext): Boolean
}

interface Transaction : Transactor {
    fun getRawData(): ByteArray
    fun getRID(): ByteArray  // transaction uniquie identifier which is used as a reference to it
    fun getHash(): ByteArray // hash of transaction content
}

interface BlockBuildingStrategy {
    fun shouldBuildBlock(): Boolean
    fun blockCommitted(blockData: BlockData)
    fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean
}

/**
 * BlockchainConfiguration is a stateless objects which describes
 * an individual blockchain instance within Postchain system
 */
interface BlockchainConfiguration {
    val chainID: Long
    val traits: Set<String>

    fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader
    fun decodeWitness(rawWitness: ByteArray): BlockWitness
    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(ctx: EContext): BlockBuilder
    fun makeBlockQueries(storage: Storage): BlockQueries
    fun initializeDB(ctx: EContext)
    fun getBlockBuildingStrategy(blockQueries: BlockQueries, transactionQueue: TransactionQueue): BlockBuildingStrategy
}

interface BlockchainContext {
    val blockchainRID: ByteArray
    val nodeID: Int
    val chainID: Long
}

interface BlockchainConfigurationFactory {
    fun makeBlockchainConfiguration(configurationData: Any, context: BlockchainContext): BlockchainConfiguration
}

interface TransactionFactory {
    fun decodeTransaction(data: ByteArray): Transaction
}

interface TransactionQueue {
    fun takeTransaction(): Transaction?
    fun enqueue(tx: Transaction): Boolean
    fun getTransactionStatus(txHash: ByteArray): TransactionStatus
    fun getTransactionQueueSize(): Int
    fun removeAll(transactionsToRemove: Collection<Transaction>)
    fun rejectTransaction(tx: Transaction, reason: Exception?)
}

interface BlockQueries {
    fun getBlockSignature(blockRID: ByteArray): Promise<Signature, Exception>
    fun getBestHeight(): Promise<Long, Exception>
    fun getBlockRids(height: Long): Promise<List<ByteArray>, Exception>
    fun getBlockAtHeight(height: Long): Promise<BlockDataWithWitness, Exception>
    fun getBlockHeader(blockRID: ByteArray): Promise<BlockHeader, Exception>

    fun getBlockTransactionRids(blockRID: ByteArray): Promise<List<ByteArray>, Exception>
    fun getTransaction(txRID: ByteArray): Promise<Transaction?, Exception>
    fun query(query: String): Promise<String, Exception>
    fun isTransactionConfirmed(txRID: ByteArray): Promise<Boolean, Exception>
}

interface BlockBuilder {
    fun begin()
    fun appendTransaction(tx: Transaction)
    fun finalizeBlock()
    fun finalizeAndValidate(bh: BlockHeader)
    fun getBlockData(): BlockData
    fun getBlockWitnessBuilder(): BlockWitnessBuilder?
    fun commit(w: BlockWitness?)
}

class InitialBlockData(val blockIID: Long, val chainID: Long, val prevBlockRID: ByteArray, val height: Long, val timestamp: Long)

enum class TransactionStatus { UNKNOWN, REJECTED, WAITING, CONFIRMED }

interface BlockStore {
    fun beginBlock(ctx: EContext): InitialBlockData
    fun addTransaction(bctx: BlockEContext, tx: Transaction): TxEContext
    fun finalizeBlock(bctx: BlockEContext, bh: BlockHeader)
    fun commitBlock(bctx: BlockEContext, w: BlockWitness?)
    fun getBlockHeight(ctx: EContext, blockRID: ByteArray): Long? // returns null if not found
    fun getBlockRIDs(ctx: EContext, height: Long): List<ByteArray> // returns null if height is out of range
    fun getLastBlockHeight(ctx: EContext): Long // height of the last block, first block has height 0
    fun getLastBlockTimestamp(ctx: EContext): Long
    //    fun getBlockData(ctx: EContext, blockRID: ByteArray): BlockData
    fun getWitnessData(ctx: EContext, blockRID: ByteArray): ByteArray

    fun getBlockHeader(ctx: EContext, blockRID: ByteArray): ByteArray

    fun getTxRIDsAtHeight(ctx: EContext, height: Long): Array<ByteArray>
    fun getTxBytes(ctx: EContext, rid: ByteArray): ByteArray?
    fun getBlockTransactions(ctx: EContext, blockRID: ByteArray): List<ByteArray>

    fun isTransactionConfirmed(ctx: EContext, txRID: ByteArray): Boolean
    fun getConfirmationProofMaterial(ctx: EContext, txRID: ByteArray): Any
}

interface ConfigurationDataStore {
    fun getConfigurationData(context: EContext, height: Long): ByteArray
    fun addConfigurationData(context: EContext, height: Long, data: ByteArray): Long
}

/**
 * A block builder which automatically manages the connection
 */
interface ManagedBlockBuilder : BlockBuilder {
    fun maybeAppendTransaction(tx: Transaction): Exception?
    fun rollback()
}

interface Shutdownable {
    fun shutdown()
}

interface BlockchainEngine: Shutdownable {
    fun initializeDB()
    fun addBlock(block: BlockDataWithWitness)
    fun loadUnfinishedBlock(block: BlockData): ManagedBlockBuilder
    fun buildBlock(): ManagedBlockBuilder
    fun getTransactionQueue(): TransactionQueue
    fun getBlockBuildingStrategy(): BlockBuildingStrategy
    fun getBlockQueries(): BlockQueries
}

interface BlockchainProcess: Shutdownable {
    fun getEngine(): BlockchainEngine
}

interface BlockchainInfrastructure {
    fun parseConfigurationString(rawData: String, format: String): ByteArray
    fun makeBlockchainConfiguration(rawConfigurationData: ByteArray, context: BlockchainContext): BlockchainConfiguration
    fun makeBlockchainEngine(configuration: BlockchainConfiguration): BlockchainEngine
    fun makeBlockchainProcess(engine: BlockchainEngine): BlockchainProcess
}

interface BlockchainInfrastructureFactory {
    fun makeBlockchainInfrastructure(config: Configuration): BlockchainInfrastructure
}

