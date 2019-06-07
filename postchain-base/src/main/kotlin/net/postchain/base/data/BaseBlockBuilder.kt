// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.merkle.Hash
import net.postchain.common.toHex
import net.postchain.core.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import java.util.*

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
        val cryptoSystem: CryptoSystem,
        eContext: EContext,
        store: BlockStore,
        txFactory: TransactionFactory,
        val subjects: Array<ByteArray>,
        val blockSigMaker: SigMaker,
        val blockchainRelatedInfoDependencyList: List<BlockchainRelatedInfo>
): AbstractBlockBuilder(eContext, store, txFactory) {

    companion object : KLogging()

    private val calc = GtvMerkleHashCalculator(cryptoSystem)

    /**
     * Computes the root hash for the Merkle tree of transactions currently in a block
     *
     * @return The Merkle tree root hash
     */
    fun computeRootHash(): ByteArray {
        val digests = rawTransactions.map { txFactory.decodeTransaction(it).getHash() }

        val gtvArr = gtv(digests.map {gtv(it)})

        return gtvArr.merkleHash(calc)
    }

    /**
     * Create block header from initial block data
     *
     * @return Block header
     */
    override fun makeBlockHeader(): BlockHeader {
        var timestamp = System.currentTimeMillis()
        if (timestamp <= initialBlockData.timestamp) {
            // if our time is behind the timestamp of most recent block, do a minimal increment
            timestamp = initialBlockData.timestamp + 1
        }

        val rootHash = computeRootHash()
        if (logger.isDebugEnabled) {
            logger.debug("Create Block header. Root hash: ${rootHash.toHex()}, "+
                    " prev block: ${initialBlockData.prevBlockRID.toHex()} ," +
                    " height = ${initialBlockData.height} ")
        }
        val bh = BaseBlockHeader.make(cryptoSystem, initialBlockData, rootHash , timestamp)
        if (logger.isDebugEnabled) {
            logger.debug("Block header created with block RID: ${bh.blockRID.toHex()}.")
        }
        return bh
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
    override fun validateBlockHeader(blockHeader: BlockHeader): ValidationResult {
        val header = blockHeader as BaseBlockHeader

        val computedMerkleRoot = computeRootHash()
        // TODO: Remove these "debug" lines 2019-06-01 (nice to keep for now since we'll see what tests are not updated)
        println("computed MR: ${computedMerkleRoot.toHex()}")
        println("header MR: ${header.blockHeaderRec.getMerkleRootHash().toHex()}")
        return when {
            !Arrays.equals(header.prevBlockRID, initialBlockData.prevBlockRID) ->
                ValidationResult(false, "header.prevBlockRID != initialBlockData.prevBlockRID," +
                        "( ${header.prevBlockRID.toHex()} != ${initialBlockData.prevBlockRID.toHex()} ), "+
                        " height: ${header.blockHeaderRec.getHeight()} and ${initialBlockData.height} ")

            header.blockHeaderRec.getHeight() != initialBlockData.height ->
                ValidationResult(false, "header.blockHeaderRec.height != initialBlockData.height")

            bctx.timestamp >= header.timestamp ->
                ValidationResult(false, "bctx.timestamp >= header.timestamp")

            !header.checkIfAllBlockchainDependenciesArePresent(blockchainRelatedInfoDependencyList) ->
                ValidationResult(false, "checkIfAllBlockchainDependenciesArePresent() is false")

            !Arrays.equals(header.blockHeaderRec.getMerkleRootHash(), computedMerkleRoot) -> // Do this last since most expensive check!
                ValidationResult(false, "header.blockHeaderRec.rootHash != computeRootHash()")

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
        if (!(blockWitness is MultiSigBlockWitness)) {
            throw ProgrammerMistake("Invalid BlockWitness impelmentation.")
        }
        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getRequiredSigCount())
        for (signature in blockWitness.getSignatures()) {
            witnessBuilder.applySignature(signature)
        }
        return witnessBuilder.isComplete()
    }

    /**
     * There are two cases:
     * 1. We get a block from a peer, and thus must check that it is correct depending on our previous block dependencies.
     * 2. We are build the block ourselves, and thus trust previous deps are ok
     *
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


    /**
     * Find the blockchain dependencies for the block we are building right now, using the block header we got from the peer.
     *
     * @param previousDependencies are the dependencies from the previous/last block of this chain
     * @param partialBlockHeader is the header we have been sent
     * @return the blockchain dependencies for the block we are building right now
     */
    private fun buildBlockchainDependenciesFromHeader(partialBlockHeader: BlockHeader): BlockchainDependencies {
        return if (blockchainRelatedInfoDependencyList.size > 0) {
            val baseBH = partialBlockHeader as BaseBlockHeader
            val givenDependencies = baseBH.blockHeightDependencyArray
            if (givenDependencies.size != blockchainRelatedInfoDependencyList.size) {
                throw BadDataMistake(BadDataType.BAD_CONFIGURATION,
                        "The given block header has ${givenDependencies.size} dependencies but our configuration" +
                                " requires ${blockchainRelatedInfoDependencyList.size} ")
            } else {
                // Get the dependencies from previous block
                val previousDependencies = store.getLastBlockDependencies(ectx)

                val resList = mutableListOf<BlockchainDependency>()
                var i = 0
                for (configBcInfo in blockchainRelatedInfoDependencyList) {
                    val prevDep = previousDependencies.getFromChainId(configBcInfo.chainId!!)
                    val dep = findHeightForGivenDependency(configBcInfo, givenDependencies[i], prevDep)
                    resList.add(dep)
                    i++
                }
                BlockchainDependencies(resList)
            }
        } else {
            BlockchainDependencies(listOf()) // No dependencies
        }
    }

    /**
     * Find the height for the given dependency
     *
     * (Since dependencies is tricky it get's broken we spend some time code on proper error messages here)
     *
     * @param configurationBcInfo is the dependency we get from the configuration
     * @param givenBlockRid is the dependency info we get from the header
     * @param prevDependency is the dependency of the previous block regarding this chain
     * @return a proper [BlockchainDependency] instance with the correct height in it.
     */
    private fun findHeightForGivenDependency(configurationBcInfo: BlockchainRelatedInfo, givenBlockRid: Hash?, prevDependency: BlockchainDependency?): BlockchainDependency {
        return if (prevDependency == null || prevDependency.heightDependency == null) {
            // ---- No height for the previous block's dependency ----
            if (givenBlockRid == null) {
                logger.debug { "Dep chainId: ${configurationBcInfo.chainId}. Last block had height -1 for this dep, and " +
                        "the header needs height -1 for this dep. Don't do any more checks" }
                BlockchainDependency(configurationBcInfo, null)
            } else {
                val dbHeight = validatePrevHeightExists(givenBlockRid, configurationBcInfo)
                buildBlockchainDependencyWithHeight(configurationBcInfo, -1L, dbHeight, givenBlockRid)
            }
        } else {
            // ---- We have a height for previous block's dependency, so we have to check it ----
            val previousHeight = prevDependency.heightDependency!!.height!!

            if (givenBlockRid == null) {
                throw BadDataMistake(BadDataType.GIVEN_DEPENDENCY_HEIGHT_TOO_LOW,
                        "Block we received must include a dependency for blockchain $configurationBcInfo " +
                                ", since last block had this dependency of height: $previousHeight ")
            } else {
                val dbHeight = validatePrevHeightExists(givenBlockRid, configurationBcInfo)
                if(dbHeight < previousHeight) {
                    throw BadDataMistake(BadDataType.GIVEN_DEPENDENCY_HEIGHT_TOO_LOW,
                        "Block we received includes a dependency for blockchain: $configurationBcInfo " +
                                ", with height $dbHeight, but the dependency of last block had height: $previousHeight for this dependency.")
                } else {
                    buildBlockchainDependencyWithHeight(configurationBcInfo, previousHeight, dbHeight, givenBlockRid)
                }
            }
        }
    }

    private fun validatePrevHeightExists(givenBlockRid: Hash, configurationBcInfo: BlockchainRelatedInfo): Long {
        val dbHeight = store.getBlockHeightFromAnyBlockchain(bctx, givenBlockRid, configurationBcInfo.chainId!!)
        return dbHeight ?:
            // Ok to bang out if we are behind in blocks. Discussed this with Alex (2019-03-29)
            throw BadDataMistake(BadDataType.MISSING_DEPENDENCY,
                    "We are not ready to accept the block since block dependency (RID: ${givenBlockRid.toHex()}) " +
                            " for BC: $configurationBcInfo is missing. ")
    }

    private fun buildBlockchainDependencyWithHeight(configurationBcInfo: BlockchainRelatedInfo, previousHeight: Long,
                                                    dbHeight: Long, givenBlockRid: Hash): BlockchainDependency {
        logger.debug { "Dep chainId: ${configurationBcInfo.chainId}. Last block had height $previousHeight for this dep, " +
                "and the header needs height $dbHeight for this dep (which we have in DB). All is well" }
        return BlockchainDependency(configurationBcInfo, HeightDependency(givenBlockRid, dbHeight))
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

        val witnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, _blockData!!.header, subjects, getRequiredSigCount())
        witnessBuilder.applySignature(blockSigMaker.signDigest(_blockData!!.header.blockRID)) // TODO: POS-04_sig
        return witnessBuilder
    }

    /**
     * Return the number of signature required for a finalized block to be deemed valid
     *
     * @return An integer representing the threshold value
     */
    protected open fun getRequiredSigCount(): Int {
        val requiredSigs: Int
        if (subjects.size == 1)
            requiredSigs = 1
        else if (subjects.size == 3) {
            requiredSigs = 3
        } else {
            val maxFailedNodes = Math.floor(((subjects.size - 1) / 3).toDouble())
            //return signers.signers.length - maxFailedNodes;
            requiredSigs = 2 * maxFailedNodes.toInt() + 1
        }
        return requiredSigs
    }

}