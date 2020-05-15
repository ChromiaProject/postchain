// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.merkle.Hash
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.getBFTRequiredSignatureCount
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.lang.Long.max
import java.nio.file.Paths

/**
 * BaseBlockBuilder is used to aid in building blocks, including construction and validation of block header and witness
 *
 * @property cryptoSystem Crypto utilities
 * @property eContext Connection context including blockchain and node identifiers
 * @property store For database access
 * @property txFactory Used for serializing transaction data
 * @property subjects Public keys for nodes authorized to sign blocks
 * @property blockchainDependencies holds the blockchain RIDs this blockchain depends on
 * @property blockSigMaker used to produce signatures on blocks for local node
 */
open class BaseBlockBuilder(
        blockchainRID: BlockchainRid,
        val cryptoSystem: CryptoSystem,
        val eContext: EContext,
        store: BlockStore,
        txFactory: TransactionFactory,
        private val subjects: Array<ByteArray>,
        private val blockSigMaker: SigMaker,
        private val blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>,
        private val usingHistoricBRID: Boolean,
        private val maxBlockSize : Long = 20*1024*1024, // 20mb
        private val maxBlockTransactions : Long = 100,
        private val chunkSize : Int = 1024,
        private val snapshotFolder: String = "snapshot"
): AbstractBlockBuilder(eContext, blockchainRID, store, txFactory) {

    companion object : KLogging()

    private val calc = GtvMerkleHashCalculator(cryptoSystem)

    private var blockSize : Long = 0L

    /**
     * Computes the root hash for the Merkle tree of transactions currently in a block
     *
     * @return The Merkle tree root hash
     */
    private fun computeMerkleRootHash(): ByteArray {
        val digests = rawTransactions.map { txFactory.decodeTransaction(it).getHash() }

        val gtvArr = gtv(digests.map { gtv(it) })

        return gtvArr.merkleHash(calc)
    }

    /**
     * Computes the root hash for the Merkle tree of snapshot at the block
     *
     * @return The Merkle tree root hash of snapshot
     */
    private fun computeSnapshotMerkleRootHash(withSnapshot: Boolean = false): ByteArray {
        // break circuit to return empty merkle hash
        if (!withSnapshot) {
            val gtvArr = gtv(GtvNull)
            return gtvArr.merkleHash(calc)
        }
        // build snapshot tree
        var idx = 0L
        var offset = 0L
        var listOfChunk = listOf<TreeElement?>()
        while (true) {
            var rows = store.getChunkData(ectx, chunkSize, offset, initialBlockData.height)
            if (rows.isEmpty()) {
                break
            }
            val leafs = rows.map { row ->
                TLeaf(longToKey(row.id.asInteger()), row)
            }
            val data = buildChunked(SimpleChunkAccess(leafs))

            // Serialize tree chunk object into file with name is root hash of the tree chunk
            if (data != null) {
                val path = Paths.get("").toAbsolutePath().normalize().toString() + File.separator +
                        snapshotFolder + File.separator + ectx.chainID + File.separator +
                        initialBlockData.height + File.separator + data.hash()
                val file = File(path)
                file.parentFile.mkdirs()
                file.createNewFile()
                ObjectOutputStream(FileOutputStream(file)).use {
                    it.writeObject(data)
                    it.flush()
                    it.close()
                }
            }

            // convert raw data snapshot tree into hash snapshot tree to reduce snapshot size
            listOfChunk = listOfChunk.plus(mergeHashTrees(listOf(data as TreeElement)))
            if (rows.size < chunkSize) {
                break
            }
            idx++
            offset = idx * chunkSize
        }

        // only need to return hash snapshot tree
        val root = mergeHashTrees(listOfChunk as List<TreeElement>)

        return root!!.hash().toByteArray()
    }

    override fun begin(partialBlockHeader: BlockHeader?) {
        if (partialBlockHeader == null && usingHistoricBRID) {
            throw UserMistake("Cannot build new blocks in historic mode (check configuration)")
        }
        super.begin(partialBlockHeader)
    }

    /**
     * Create block header from initial block data
     *
     * @return Block header
     */
    override fun makeBlockHeader(withSnapshot: Boolean): BlockHeader {
        // If our time is behind the timestamp of most recent block, do a minimal increment
        val timestamp = max(System.currentTimeMillis(), initialBlockData.timestamp + 1)
        val rootHash = computeMerkleRootHash()
        val snapshotRootHash = computeSnapshotMerkleRootHash(withSnapshot)
        return BaseBlockHeader.make(cryptoSystem, initialBlockData, rootHash, snapshotRootHash, timestamp)
    }

    /**
     * Validate block header:
     * - check that previous block RID is used in this block
     * - check for correct height
     * - check that timestamp occurs after previous blocks timestamp
     * - check if all required dependencies are present
     * - check for correct root hash
     *
     * @param blockHeader The block header to validate
     */
    override fun validateBlockHeader(blockHeader: BlockHeader, withSnapshot: Boolean): ValidationResult {
        val header = blockHeader as BaseBlockHeader

        val computedMerkleRoot = computeMerkleRootHash()
        val computedSnapshotMerkleRoot = computeSnapshotMerkleRootHash(withSnapshot)
        return when {
            !header.prevBlockRID.contentEquals(initialBlockData.prevBlockRID) ->
                ValidationResult(false, "header.prevBlockRID != initialBlockData.prevBlockRID," +
                        "( ${header.prevBlockRID.toHex()} != ${initialBlockData.prevBlockRID.toHex()} ), " +
                        " height: ${header.blockHeaderRec.getHeight()} and ${initialBlockData.height} ")

            header.blockHeaderRec.getHeight() != initialBlockData.height ->
                ValidationResult(false, "header.blockHeaderRec.height != initialBlockData.height")

            bctx.timestamp >= header.timestamp ->
                ValidationResult(false, "bctx.timestamp >= header.timestamp")

            !header.checkIfAllBlockchainDependenciesArePresent(blockchainRelatedInfoDependencyList) ->
                ValidationResult(false, "checkIfAllBlockchainDependenciesArePresent() is false")

            !header.blockHeaderRec.getMerkleRootHash().contentEquals(computedMerkleRoot) -> // Do this last since most expensive check!
                ValidationResult(false, "header.blockHeaderRec.rootHash != computeRootHash()")

            !header.blockHeaderRec.getSnapshotMerkleRootHash().contentEquals(computedSnapshotMerkleRoot) ->
                ValidationResult(false, "header.blockHeaderRec.snapshotRootHash != computeSnapshotRootHash(), remote: ${header.blockHeaderRec.getSnapshotMerkleRootHash().toHex()}, local:${computedSnapshotMerkleRoot.toHex()}, at ${initialBlockData.height}")

            else -> ValidationResult(true)
        }
    }

    /**
     * Validates the following:
     *  - Witness is of a correct implementation
     *  - The signatures are valid with respect to the block being signed
     *  - The number of signatures exceeds the threshold necessary to deem the block itself valid
     *
     *  @param blockWitness The witness to be validated
     *  @throws ProgrammerMistake Invalid BlockWitness implementation
     */
    override fun validateWitness(blockWitness: BlockWitness): Boolean {
        if (blockWitness !is MultiSigBlockWitness) {
            throw ProgrammerMistake("Invalid BlockWitness implementation.")
        }
        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getBFTRequiredSignatureCount(subjects.size))
        for (signature in blockWitness.getSignatures()) {
            witnessBuilder.applySignature(signature)
        }
        return witnessBuilder.isComplete()
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
                        val dbHeight = store.getBlockHeightFromAnyBlockchain(bctx, blockRid, bcInfo.chainId!!)
                        if (dbHeight != null) {
                            BlockchainDependency(bcInfo, HeightDependency(blockRid, dbHeight))
                        } else {
                            // Ok to bang out if we are behind in blocks. Discussed this with Alex (2019-03-29)
                            throw BadDataMistake(BadDataType.MISSING_DEPENDENCY,
                                    "We are not ready to accept the block since block dependency (RID: ${blockRid.toHex()}) is missing. ")
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

        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getBFTRequiredSignatureCount(subjects.size))
        witnessBuilder.applySignature(blockSigMaker.signDigest(_blockData!!.header.blockRID)) // TODO: POS-04_sig
        return witnessBuilder
    }

    override fun appendTransaction(tx: Transaction) {
        super.appendTransaction(tx)
        blockSize = transactions.map { it.getRawData().size.toLong() }.sum()
        if (blockSize >= maxBlockSize) {
            throw UserMistake("block size exceeds max block size $maxBlockSize bytes")
        } else if (transactions.size >= maxBlockTransactions) {
            throw UserMistake("Number of transactions exceeds max $maxBlockTransactions transactions in block")
        }
    }


}