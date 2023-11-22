// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.AbstractBlockBuilder
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.BlockchainDependencies
import net.postchain.base.BlockchainDependency
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.HeightDependency
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition.Begin
import net.postchain.base.SpecialTransactionPosition.End
import net.postchain.base.TxEventSink
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.base.extension.FAILED_CONFIG_HASH_EXTRA_HEADER
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BadBlockException
import net.postchain.core.BadConfigurationException
import net.postchain.core.ConfigurationMismatchException
import net.postchain.core.EContext
import net.postchain.core.FailedConfigurationMismatchException
import net.postchain.core.MissingDependencyException
import net.postchain.core.PrevBlockMismatchException
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.ValidationResult
import net.postchain.core.ValidationResult.Result.INVALID_EXTRA_DATA
import net.postchain.core.ValidationResult.Result.OK
import net.postchain.core.ValidationResult.Result.PREV_BLOCK_MISMATCH
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockStore
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.lang.Long.max
import java.time.Clock

/**
 * BaseBlockBuilder is used to aid in building blocks, including construction and validation of block header and witness
 *
 * @param blockchainRID
 * @property cryptoSystem Crypto utilities
 * @param    eContext Connection context including blockchain and node identifiers
 * @param store For database access
 * @property specialTxHandler is the main entry point for special transaction handling.
 * @property subjects Public keys for nodes authorized to sign blocks
 * @property blockSigMaker used to produce signatures on blocks for local node
 * @property blockWitnessProvider
 * @property blockchainRelatedInfoDependencyList holds the blockchain RIDs this blockchain depends on
 * @property extensions are extensions to the block builder, usually helping with handling of special transactions.
 * @property usingHistoricBRID
 * @property maxBlockSize
 * @property maxBlockTransactions
 * @property maxSpecialEndTransactionSize
 * @property suppressSpecialTransactionValidation
 * @property maxBlockFutureTime
 * @property clock
 *
 */
open class BaseBlockBuilder(
        blockchainRID: BlockchainRid,
        val cryptoSystem: CryptoSystem,
        eContext: EContext,
        store: BlockStore,
        private val specialTxHandler: SpecialTransactionHandler,
        val subjects: Array<ByteArray>,
        val blockSigMaker: SigMaker,
        override val blockWitnessProvider: BlockWitnessProvider,
        private val blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
        val extensions: List<BaseBlockBuilderExtension>,
        private val usingHistoricBRID: Boolean,
        val maxBlockSize: Long,
        val maxBlockTransactions: Long,
        val maxSpecialEndTransactionSize: Long,
        val suppressSpecialTransactionValidation: Boolean,
        private val maxBlockFutureTime: Long,
        val clock: Clock = Clock.systemUTC()
) : AbstractBlockBuilder(eContext, blockchainRID, store) {

    companion object : KLogging()

    private val eventProcessors = mutableMapOf<String, TxEventSink>()

    private val calc = GtvMerkleHashCalculator(cryptoSystem)

    internal var blockSize: Long = 0L // not private due to test access
    private var haveSpecialEndTransaction = false
    private var isSpecialEndTransaction = false

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
                    throw BadBlockException("Block builder extensions clash: ${kv.key}")
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
    override fun makeBlockHeader(timestamp: Long): BlockHeader {
        // If our time is behind the timestamp of most recent block, do a minimal increment
        val safeTimestamp = max(timestamp, initialBlockData.timestamp + 1)
        val rootHash = computeMerkleRootHash()
        return BaseBlockHeader.make(GtvMerkleHashCalculator(cryptoSystem), initialBlockData, rootHash, safeTimestamp, finalizeExtensions())
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
                            throw MissingDependencyException("We are not ready to accept the block since block dependency (blockRID: ${blockRid.toHex()} from blockchainRID: ${bcInfo.blockchainRid.toHex()}) is missing.")
                        }
                    } else {
                        BlockchainDependency(bcInfo, null) // No blocks required -> allowed
                    }
                    resList.add(dep)
                }
                BlockchainDependencies(resList)
            } else {
                throw BadConfigurationException("The given block header has ${givenDependencies.size} dependencies our configuration requires ${blockchainRelatedInfoDependencyList.size} ")
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
    override fun finalizeBlock(timestamp: Long): BlockHeader {
        if (buildingNewBlock && specialTxHandler.needsSpecialTransaction(End)) {
            isSpecialEndTransaction = true
            appendTransaction(specialTxHandler.createSpecialTransaction(End, bctx))
        }
        return super.finalizeBlock(timestamp)
    }

    /**
     * In this case we already have the [BlockHeader] meaning it's a block we got from someone else, and thus
     * we will validate it.
     *
     * @param blockHeader is the header for the block we are working on.
     */
    override fun finalizeAndValidate(blockHeader: BlockHeader) {
        if (specialTxHandler.needsSpecialTransaction(End) && !haveSpecialEndTransaction)
            throw BadBlockException("End special transaction is missing")
        val extraData = finalizeExtensions()
        val validationResult = validateBlockHeader(blockHeader, extraData)
        when (validationResult.result) {
            OK -> {
                store.finalizeBlock(bctx, blockHeader)
                _blockData = BlockData(blockHeader, rawTransactions)
                finalized = true
            }

            PREV_BLOCK_MISMATCH -> throw PrevBlockMismatchException(validationResult.message)
            INVALID_EXTRA_DATA -> {
                if (blockHeader is BaseBlockHeader && blockHeader.extraData[CONFIG_HASH_EXTRA_HEADER] != extraData[CONFIG_HASH_EXTRA_HEADER]) {
                    throw ConfigurationMismatchException(validationResult.message)
                } else if (blockHeader is BaseBlockHeader && blockHeader.extraData[FAILED_CONFIG_HASH_EXTRA_HEADER] != extraData[FAILED_CONFIG_HASH_EXTRA_HEADER]) {
                    throw FailedConfigurationMismatchException(validationResult.message)
                } else {
                    throw BadBlockException(validationResult.message)
                }
            }

            else -> throw BadBlockException(validationResult.message)
        }
    }

    private fun checkSpecialTransaction(tx: Transaction) {
        if (haveSpecialEndTransaction) {
            throw BadBlockException("Cannot append transactions after end special transaction")
        }
        val expectBeginTx = specialTxHandler.needsSpecialTransaction(Begin) && transactions.size == 0
        if (tx.isSpecial()) {
            if (expectBeginTx) {
                if (!suppressSpecialTransactionValidation && !specialTxHandler.validateSpecialTransaction(Begin, tx, bctx)) {
                    throw BadBlockException("Special transaction validation failed: $Begin")
                }
                return // all is well, the first transaction is special and valid
            }
            val needEndTx = specialTxHandler.needsSpecialTransaction(End)
            if (!needEndTx) {
                throw BadBlockException("Found unexpected special transaction")
            }
            if (!suppressSpecialTransactionValidation && !specialTxHandler.validateSpecialTransaction(End, tx, bctx)) {
                throw BadBlockException("Special transaction validation failed: $End")
            }
            haveSpecialEndTransaction = true
        } else if (expectBeginTx) {
            throw BadBlockException("First transaction must be special transaction")
        }
    }

    override fun appendTransaction(tx: Transaction) {
        val addSpecialEndTransactionBuffer = !isSpecialEndTransaction && specialTxHandler.needsSpecialTransaction(End)
        val transactionsSize = transactions.size + if (addSpecialEndTransactionBuffer) 1 else 0
        val newBlockSize = blockSize + tx.getRawData().size + if (addSpecialEndTransactionBuffer) maxSpecialEndTransactionSize else 0
        if (newBlockSize > maxBlockSize) {
            throw BadBlockException("block size exceeds max block size $maxBlockSize bytes")
        } else if (transactionsSize >= maxBlockTransactions) {
            throw BadBlockException("Number of transactions exceeds max $maxBlockTransactions transactions in block")
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
                clock.millis(),
                maxBlockFutureTime,
                nrOfDependencies,
                extraData
        )
    }

    fun shouldStopBuildingBlock(maxBlockTransactions: Long): Boolean {
        val needsSpecialEndTransaction = specialTxHandler.needsSpecialTransaction(End)
        val transactionsSize = transactions.size + if (needsSpecialEndTransaction) 1 else 0
        val currentSize = blockSize + if (needsSpecialEndTransaction) maxSpecialEndTransactionSize else 0
        return transactionsSize >= maxBlockTransactions || currentSize >= maxBlockSize
    }
}