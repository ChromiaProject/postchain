// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.SpecialTransactionPosition.Begin
import net.postchain.base.SpecialTransactionPosition.End
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.core.ValidationResult.Result.*
import net.postchain.core.block.*
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.lang.Long.max
import java.util.*

/**
 * BaseBlockBuilder is used to aid in building blocks, including construction and validation of block header and witness
 *
 * @property blockchainRID
 * @property cryptoSystem Crypto utilities
 * @property eContext Connection context including blockchain and node identifiers
 * @property store For database access
 * @property txFactory Used for serializing transaction data
 * @property specialTxHandler is the main entry point for special transaction handling.
 * @property subjects Public keys for nodes authorized to sign blocks
 * @property blockSigMaker used to produce signatures on blocks for local node
 * @property blockWitnessProvider
 * @property blockchainRelatedInfoDependencyList holds the blockchain RIDs this blockchain depends on
 * @property extensions are extensions to the block builder, usually helping with handling of special transactions.
 * @property usingHistoricBRID
 * @property maxBlockSize
 * @property maxBlockTransactions
 */
open class BaseBlockBuilder(
        blockchainRID: BlockchainRid,
        val cryptoSystem: CryptoSystem,
        eContext: EContext,
        store: BlockStore,
        txFactory: TransactionFactory,
        val specialTxHandler: SpecialTransactionHandler,
        val subjects: Array<ByteArray>,
        val blockSigMaker: SigMaker,
        override val blockWitnessProvider: BlockWitnessProvider,
        val blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
        val extensions: List<BaseBlockBuilderExtension>,
        val usingHistoricBRID: Boolean,
        val maxBlockSize: Long,
        val maxBlockTransactions: Long,
        maxTxExecutionTime: Long
) : AbstractBlockBuilder(eContext, blockchainRID, store, txFactory, maxTxExecutionTime) {

    companion object : KLogging()

    private val eventProcessors = mutableMapOf<String, TxEventSink>()

    private val calc = GtvMerkleHashCalculator(cryptoSystem)

    private var blockSize: Long = 0L
    private var haveSpecialEndTransaction = false

    /**
     * Computes the root hash for the Merkle tree of transactions currently in a block
     *
     * @return The Merkle tree root hash
     */
    override fun computeMerkleRootHash(): ByteArray {
        val digestsGtv = gtv(transactions.map { gtv(it.getHash()) })

        return digestsGtv.merkleHash(calc)
    }

    /**
     * Adds an [TxEventSink] to this block builder.
     */
    fun installEventProcessor(type: String, sink: TxEventSink) {
        if (type in eventProcessors) throw ProgrammerMistake("Conflicting event processors in block builder, type $type")
        eventProcessors[type] = sink
    }

    /**
     * Will send the given "data" to the correct event sink.
     *
     * @param ctxt is just the context
     * @param type is the [TxEventSink] we want to send data to, if this sink isn't found, throw exception, b/c we do
     *             not want these messages to accidentally get lost.
     * @param data is the data we should send to the [TxEventSink]
     */
    override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
        when (val proc = eventProcessors[type]) {
            null -> throw ProgrammerMistake("Event sink for $type not found")
            else -> proc.processEmittedEvent(ctxt, type, data)
        }
    }

    /**
     * Retrieve initial block data and set block context.
     *
     * NOTE: For NEW blocks we must remember to add special transactions (old blocks should not be tampered with).
     *
     * @param partialBlockHeader might hold the header.
     */
    override fun begin(partialBlockHeader: BlockHeader?) {
        if (partialBlockHeader == null && usingHistoricBRID) {
            throw UserMistake("Cannot build new blocks in historic mode (check configuration)")
        }
        super.begin(partialBlockHeader)
        for (x in extensions) x.init(this.bctx, this)
        if (buildingNewBlock && specialTxHandler.needsSpecialTransaction(Begin)) {
            val stx = specialTxHandler.createSpecialTransaction(Begin, bctx)
            appendTransaction(stx)
        }
    }

    open fun finalizeExtensions(): Map<String, Gtv> {
        val m = mutableMapOf<String, Gtv>()
        for (x in extensions) {
            for (kv in x.finalize()) {
                if (kv.key in m) {
                    throw BlockValidationMistake("Block builder extensions clash: ${kv.key}")
                }
                m[kv.key] = kv.value
            }
        }
        return m
    }

    /**
     * Create block header from initial block data
     *
     * @return Block header
     */
    override fun makeBlockHeader(): BlockHeader {
        // If our time is behind the timestamp of most recent block, do a minimal increment
        val timestamp = max(System.currentTimeMillis(), initialBlockData.timestamp + 1)
        val rootHash = computeMerkleRootHash()
        return BaseBlockHeader.make(GtvMerkleHashCalculator(cryptoSystem), initialBlockData, rootHash, timestamp, finalizeExtensions())
    }

    /**
     * @param partialBlockHeader if this is given, we should get the dependency information from the header, else
     *                           we should get the heights from the DB.
     * @return all dependencies to other blockchains and their heights this block needs.
     */
    override fun buildBlockchainDependencies(partialBlockHeader: BlockHeader?): BlockchainDependencies {
        return if (partialBlockHeader != null) {
            buildBlockchainDependenciesFromHeader(partialBlockHeader)
        } else {
            buildBlockchainDependenciesFromDb()
        }
    }

    private fun buildBlockchainDependenciesFromHeader(partialBlockHeader: BlockHeader): BlockchainDependencies {
        return if (blockchainRelatedInfoDependencyList.isNotEmpty()) {

            val baseBH = partialBlockHeader as BaseBlockHeader
            val givenDependencies = baseBH.blockHeightDependencyArray
            if (givenDependencies.size == blockchainRelatedInfoDependencyList.size) {

                val resList = mutableListOf<BlockchainDependency>()
                for ((i, bcInfo) in blockchainRelatedInfoDependencyList.withIndex()) {
                    val blockRid = givenDependencies[i]
                    val dep = if (blockRid != null) {
                        val dbHeight = store.getBlockHeightFromAnyBlockchain(ectx, blockRid, bcInfo.chainId!!)
                        if (dbHeight != null) {
                            BlockchainDependency(bcInfo, HeightDependency(blockRid, dbHeight))
                        } else {
                            // Ok to bang out if we are behind in blocks. Discussed this with Alex (2019-03-29)
                            throw BadDataMistake(BadDataType.MISSING_DEPENDENCY, "We are not ready to accept the block since block dependency (RID: ${blockRid.toHex()}) is missing.")
                        }
                    } else {
                        BlockchainDependency(bcInfo, null) // No blocks required -> allowed
                    }
                    resList.add(dep)
                }
                BlockchainDependencies(resList)
            } else {
                throw BadDataMistake(BadDataType.BAD_CONFIGURATION,
                        "The given block header has ${givenDependencies.size} dependencies our configuration requires ${blockchainRelatedInfoDependencyList.size} ")
            }
        } else {
            BlockchainDependencies(listOf()) // No dependencies
        }
    }

    private fun buildBlockchainDependenciesFromDb(): BlockchainDependencies {
        val resList = mutableListOf<BlockchainDependency>()
        for (bcInfo in blockchainRelatedInfoDependencyList) {
            val res: Pair<Long, Hash>? = store.getBlockHeightInfo(ectx, bcInfo.blockchainRid)
            val dep = if (res != null) {
                BlockchainDependency(bcInfo, HeightDependency(res.second, res.first))
            } else {
                BlockchainDependency(bcInfo, null) // No blocks yet, it's ok
            }
            resList.add(dep)
        }
        return BlockchainDependencies(resList)
    }

    /**
     * Retrieve the builder for block witnesses. It can only be retrieved if the block is finalized.
     *
     * @return The block witness builder
     * @throws ProgrammerMistake If the block is not finalized yet signatures can't be created since they would
     * be invalid when further transactions are added to the block
     */
    override fun getBlockWitnessBuilder(): BlockWitnessBuilder? {
        if (!finalized) {
            throw ProgrammerMistake("Block is not finalized yet.")
        }

        return blockWitnessProvider.createWitnessBuilderWithOwnSignature(_blockData!!.header)
    }

    /**
     * When this is really a new block there won't be a [BlockHeader] until after this step.
     *
     * NOTE: For NEW blocks we add special transactions (old block data must not be modified).
     *
     * @return the new [BlockHeader] we are about to create.
     */
    override fun finalizeBlock(): BlockHeader {
        if (buildingNewBlock && specialTxHandler.needsSpecialTransaction(End))
            appendTransaction(specialTxHandler.createSpecialTransaction(End, bctx))
        return super.finalizeBlock()
    }

    /**
     * In this case we already have the [BlockHeader] meaning it's a block we got from someone else, and thus
     * we will validate it.
     *
     * @param blockHeader is the header for the block we are working on.
     */
    override fun finalizeAndValidate(blockHeader: BlockHeader) {
        if (specialTxHandler.needsSpecialTransaction(End) && !haveSpecialEndTransaction)
            throw BadDataMistake(BadDataType.BAD_BLOCK, "End special transaction is missing")
        val extraData = finalizeExtensions()
        val validationResult = validateBlockHeader(blockHeader, extraData)
        when (validationResult.result) {
            OK -> {
                store.finalizeBlock(bctx, blockHeader)
                _blockData = BlockData(blockHeader, rawTransactions)
                finalized = true
            }
            PREV_BLOCK_MISMATCH -> throw BadDataMistake(BadDataType.PREV_BLOCK_MISMATCH, validationResult.message)
            else -> throw BadDataMistake(BadDataType.BAD_BLOCK, validationResult.message)
        }
    }

    private fun checkSpecialTransaction(tx: Transaction) {
        if (haveSpecialEndTransaction) {
            throw BlockValidationMistake("Cannot append transactions after end special transaction")
        }
        val expectBeginTx = specialTxHandler.needsSpecialTransaction(Begin) && transactions.size == 0
        if (tx.isSpecial()) {
            if (expectBeginTx) {
                if (!specialTxHandler.validateSpecialTransaction(Begin, tx, bctx)) {
                    throw BlockValidationMistake("Special transaction validation failed: $Begin")
                }
                return // all is well, the first transaction is special and valid
            }
            val needEndTx = specialTxHandler.needsSpecialTransaction(End)
            if (!needEndTx) {
                throw BlockValidationMistake("Found unexpected special transaction")
            }
            if (!specialTxHandler.validateSpecialTransaction(End, tx, bctx)) {
                throw BlockValidationMistake("Special transaction validation failed: $End")
            }
            haveSpecialEndTransaction = true
        } else if (expectBeginTx) {
            throw BlockValidationMistake("First transaction must be special transaction")
        }
    }

    override fun appendTransaction(tx: Transaction) {
        if (blockSize + tx.getRawData().size > maxBlockSize) {
            throw BlockValidationMistake("block size exceeds max block size $maxBlockSize bytes")
        } else if (transactions.size >= maxBlockTransactions) {
            throw BlockValidationMistake("Number of transactions exceeds max $maxBlockTransactions transactions in block")
        }
        checkSpecialTransaction(tx) // note: we check even transactions we construct ourselves
        super.appendTransaction(tx)
        blockSize += tx.getRawData().size
    }

    /**
     * @return the block RID at a certain height
     */
    private fun getBlockRidAtHeight(height: Long) = store.getBlockRID(ectx, height)

    /**
     * (Note: don't call this. We only keep this as a public function for legacy tests to work)
     */
    internal fun validateBlockHeader(blockHeader: BlockHeader, extraData: Map<String, Gtv> = mapOf()): ValidationResult {
        val nrOfDependencies = blockchainDependencies?.all()?.size ?: 0
        return GenericBlockHeaderValidator.advancedValidateAgainstKnownBlocks(
                blockHeader,
                initialBlockData,
                ::computeMerkleRootHash,
                ::getBlockRidAtHeight,
                bctx.timestamp,
                nrOfDependencies,
                extraData
        )
    }
}