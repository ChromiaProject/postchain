package net.postchain.d1.anchor

import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.base.data.MinimalBlockHeaderInfo
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.ValidationResult
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.Validation
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
class AnchorSpecialTxExtension : GTXSpecialTxExtension {

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    private val _relevantOps = setOf(OP_BLOCK_HEADER)

    val icmfReceiver = ClusterAnchorReceiver()
    lateinit var clusterManagement: ClusterManagement

    /** This is for querying ourselves, i.e. the "anchor Rell app" */
    private lateinit var module: GTXModule

    private lateinit var cryptoSystem: CryptoSystem

    override fun getRelevantOps() = _relevantOps

    override fun init(
            module: GTXModule,
            chainID: Long,
            blockchainRID: BlockchainRid,
            cs: CryptoSystem
    ) {
        this.module = module
        cryptoSystem = cs
    }

    /**
     * Asked Alex, and he said we always use "begin" for special TX (unless we are wrapping up something)
     * so we only add them here (if we have any).
     */
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = when (position) {
        SpecialTransactionPosition.Begin -> true
        SpecialTransactionPosition.End -> false
    }

    /**
     * For Anchor chain we simply pull all the messages from all the ICMF pipes and create operations.
     *
     * Since the Extension framework expects us to add a TX before and/or after the main data of a block,
     * we create ONE BIG tx with all operations in it (for the "before" position).
     * (In an anchor chain there will be no "normal" transactions, only this one big "before" special TX)
     *
     * @param position will always be "begin", we don't care about it here
     * @param bctx is the context of the anchor chain (but without BC RID)
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val pipes = icmfReceiver.getRelevantPipes()

        // Extract all packages from all pipes
        val ops = mutableListOf<OpData>()
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets()) {
                ops.addAll(handlePipe(pipe, bctx).map { buildOpData(it) })
            }
        }
        return ops
    }

    /**
     * Loop all messages for the pipe
     */
    private fun handlePipe(
            pipe: ClusterAnchorPipe,
            bctx: BlockEContext
    ): List<ClusterAnchorPacket> {
        val packets = mutableListOf<ClusterAnchorPacket>()
        val blockchainRid = pipe.id
        var currentHeight: Long = getLastAnchoredHeight(bctx, blockchainRid)
        while (pipe.mightHaveNewPackets()) {
            val clusterAnchorPacket = pipe.fetchNext(currentHeight)
            if (clusterAnchorPacket != null) {
                packets.add(clusterAnchorPacket)
                pipe.markTaken(clusterAnchorPacket.height, bctx)
                currentHeight++ // Try next height
            } else {
                break // Nothing more to find
            }
        }
        if (logger.isDebugEnabled) {
            logger.debug("Pulled ${packets.size} messages from pipeId: ${pipe.id}")
        }
        return packets
    }

    private fun getLastAnchoredHeight(ctxt: EContext, blockchainRID: BlockchainRid): Long =
            getLastAnchoredBlock(ctxt, blockchainRID)?.height ?: -1

    /**
     * Transform to [ClusterAnchorPacket] to [OpData] put arguments in correct order
     *
     * @param clusterAnchorPacket is what we get from ICMF
     * @return is the [OpData] we can use to create a special TX.
     */
    private fun buildOpData(clusterAnchorPacket: ClusterAnchorPacket): OpData {
        val gtvHeader: Gtv = GtvDecoder.decodeGtv(clusterAnchorPacket.rawHeader)
        val gtvWitness = GtvByteArray(clusterAnchorPacket.rawWitness)

        return OpData(OP_BLOCK_HEADER, arrayOf(gtv(clusterAnchorPacket.blockRid), gtvHeader, gtvWitness))
    }


    // TODO Validation discussions
    // 1. Save time:
    //    When we are the primary, we are getting headers from our local machine, shouldn't need to check it (again).
    //    (But when we are copying finished anchor block from another node we actually should validate)
    // 2. In theory we cannot be certain about what [CryptoSystem] is used by the chain, so it should be taken from the
    //    config too.

    /**
     * We look at the content of all operations (to check if the block headers are ok and nothing is missing)
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        val chainHeadersMap = mutableMapOf<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>()

        for (op in ops) {
            val anchorOpData = AnchorOpData.validateAndDecodeOpData(op) ?: return false

            val headerData = anchorOpData.headerData
            val blockRid = headerData.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
            if (!blockRid.contentEquals(anchorOpData.blockRid)) {
                logger.warn("Invalid block-rid: ${anchorOpData.blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}, expected: ${blockRid.toHex()}")
                return false
            }

            val witness = BaseBlockWitness.fromBytes(anchorOpData.witness)
            val peers = clusterManagement.getBlockchainPeers(BlockchainRid(headerData.getBlockchainRid()), headerData.getHeight())
            if (!Validation.validateBlockSignatures(cryptoSystem, headerData.getPreviousBlockRid(), GtvEncoder.encodeGtv(headerData.toGtv()), blockRid, peers.map { it.pubkey }, witness)) {
                logger.warn("Invalid block header signature for block-rid: ${blockRid.toHex()} for blockchain-rid: ${headerData.getBlockchainRid().toHex()} at height: ${headerData.getHeight()}")
                return false
            }

            val bcRid = BlockchainRid(headerData.getBlockchainRid())
            val newInfo = anchorOpData.toMinimalBlockHeaderInfo()

            val headers = chainHeadersMap.computeIfAbsent(bcRid) { mutableSetOf() }
            if (headers.all { header -> header.headerHeight != newInfo.headerHeight }) { // Rather primitive, but should be enough
                headers.add(newInfo)
            } else {
                logger.warn("Adding the same header twice, bc RID: ${bcRid.toHex()}, height ${newInfo.headerHeight}. New block: $newInfo")
                return false
            }
        }

        // Go through it chain by chain
        for ((bcRid, minimalHeaders) in chainHeadersMap) {
            // Each chain must be validated by itself b/c we must now look for gaps in the blocks etc.
            // and we pass that task to the [GenericBlockHeaderValidator]
            val validationResult = chainValidation(bctx, bcRid, minimalHeaders)
            if (validationResult.result != ValidationResult.Result.OK) {
                logger.warn(
                        "Failing to anchor a block for blockchain ${bcRid.toHex()}. ${validationResult.message}"
                )
                return false
            }
        }
        return true
    }

    /**
     * Checks all headers we have for specific chain
     *
     * General check:
     *   Initially we compare the height we see in the header with what we expect from our local table
     *   However, we might anchor multiple headers from one chain at the same time, so there is some sorting to
     *   do if we intend to discover gaps.
     *
     * @param bcRid is the chain we intend to validate
     * @param minimalHeaders is a very small data set for each header (that we use for basic validation)
     * @return the result of the validation
     */
    private fun chainValidation(
            ctxt: EContext,
            bcRid: BlockchainRid,
            minimalHeaders: Set<MinimalBlockHeaderInfo>
    ): ValidationResult {
        // Restructure to the format the Validator needs
        val myHeaderMap = minimalHeaders.associateBy { it.headerHeight }

        // Run the validator
        return GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(
                bcRid,
                myHeaderMap,
                getExpectedData(ctxt, bcRid)
        ) { getAnchoredBlockAtHeight(ctxt, bcRid, it)?.blockRid?.data }
    }

    /**
     * @return the data we expect to find, fetched from Anchor module's own tables,
     *         or null if we've never anchored any block for this chain before.
     */
    private fun getExpectedData(ctxt: EContext, bcRid: BlockchainRid): MinimalBlockHeaderInfo? =
            getLastAnchoredBlock(ctxt, bcRid)?.let {
                // We found something, return it
                MinimalBlockHeaderInfo(
                        it.blockRid,
                        null,
                        it.height
                ) // Don't care about the prev block here
            }

    /**
     * Ask the Anchor Module for last anchored block
     *
     * @param bcRid is the chain we are interested in
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getLastAnchoredBlock(ctxt: EContext, bcRid: BlockchainRid): TempBlockInfo? {
        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
                Pair("blockchain_rid", gtv(bcRidByteArr))
        )
        val block = module.query(ctxt, "get_last_anchored_block", args)
        return if (block == GtvNull) {
            null
        } else {
            TempBlockInfo.fromBlock(block)
        }
    }

    /**
     * Ask the Anchor Module for anchored block at height
     *
     * @param bcRid is the chain we are interested in
     * @param height is the block height we want to look at
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getAnchoredBlockAtHeight(ctxt: EContext, bcRid: BlockchainRid, height: Long): TempBlockInfo? {
        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
                Pair("blockchain_rid", gtv(bcRidByteArr)),
                Pair("height", gtv(height))
        )
        val block = module.query(ctxt, "get_anchored_block_at_height", args)
        return if (block == GtvNull) {
            null
        } else {
            TempBlockInfo.fromBlock(block)
        }
    }

    private fun buildArgs(vararg args: Pair<String, Gtv>): Gtv = gtv(*args)

    /**
     * Not really a domain object, just used to return some data
     */
    data class TempBlockInfo(
            val blockRid: BlockRid,
            val height: Long
    ) {
        companion object {
            fun fromBlock(block: Gtv): TempBlockInfo {
                return TempBlockInfo(
                        BlockRid(block["block_rid"]!!.asByteArray()),
                        block["block_height"]!!.asInteger()
                )
            }
        }
    }
}
