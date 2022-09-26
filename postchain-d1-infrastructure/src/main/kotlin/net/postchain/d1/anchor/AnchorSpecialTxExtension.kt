package net.postchain.d1.anchor

import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.base.data.MinimalBlockHeaderInfo
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.BlockRid
import net.postchain.core.EContext
import net.postchain.core.ValidationResult
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.icmf.IcmfPacket
import net.postchain.d1.icmf.IcmfSpecialTxExtension
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
class AnchorSpecialTxExtension : GTXSpecialTxExtension, IcmfSpecialTxExtension {

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    private val _relevantOps = setOf(OP_BLOCK_HEADER)

    override val icmfReceiver = ClusterAnchorIcmfReceiver()

    /** This is for querying ourselves, i.e. the "anchor Rell app" */
    private lateinit var module: GTXModule

    override fun getRelevantOps() = _relevantOps

    override fun init(
            module: GTXModule,
            chainID: Long,
            blockchainRID: BlockchainRid,
            cs: CryptoSystem
    ) {
        this.module = module
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
        val retList = mutableListOf<OpData>()
        for (pipe in pipes) {
            if (pipe.mightHaveNewPackets()) {
                handlePipe(pipe, retList, bctx)
            }
        }
        return retList
    }

    /**
     * Loop all messages for the pipe
     */
    private fun handlePipe(
            pipe: ClusterAnchorIcmfPipe,
            retList: MutableList<OpData>,
            bctx: BlockEContext
    ) {
        var counter = 0
        val blockchainRid = pipe.id
        var currentHeight: Long = getLastAnchoredHeight(bctx, blockchainRid)
        while (pipe.mightHaveNewPackets()) {
            val clusterAnchorPacket = pipe.fetchNext(currentHeight)
            if (clusterAnchorPacket != null) {
                retList.add(buildOpData(clusterAnchorPacket))
                pipe.markTaken(clusterAnchorPacket.height, bctx)
                currentHeight++ // Try next height
                counter++
            } else {
                break // Nothing more to find
            }
        }
        if (logger.isDebugEnabled) {
            logger.debug("Pulled $counter messages from pipeId: ${pipe.id}")
        }
    }

    private fun getLastAnchoredHeight(ctxt: EContext, blockchainRID: BlockchainRid): Long =
            getLastAnchoredBlock(ctxt, blockchainRID)?.height ?: -1

    /**
     * Transform to [IcmfPacket] to [OpData] put arguments in correct order
     *
     * @param clusterAnchorPacket is what we get from ICMF
     * @return is the [OpData] we can use to create a special TX.
     */
    private fun buildOpData(clusterAnchorPacket: ClusterAnchorPacket): OpData {
        val gtvHeader: Gtv = GtvDecoder.decodeGtv(clusterAnchorPacket.rawHeader)
        val gtvWitness = GtvByteArray(clusterAnchorPacket.rawWitness)

        return OpData(OP_BLOCK_HEADER, arrayOf(gtv(clusterAnchorPacket.blockRid), gtvHeader, gtvWitness))
    }

    /**
     * We look at the content of all operations (to check if the block headers are ok and nothing is missing)
     */
    override fun validateSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext,
            ops: List<OpData>
    ): Boolean {
        // TODO refactor this to avoid passing around mutable data so much
        val chainHeadersMap = mutableMapOf<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>()
        val valid = ops.all { isOpValidAndFillTheMinimalHeaderMap(it, chainHeadersMap) }
        if (!valid) {
            return false // Usually meaning the actual format of a header is broken
        }

        // Go through it chain by chain
        for ((bcRid, minimalHeaders) in chainHeadersMap) {
            // Each chain must be validated by itself b/c we must now look for gaps in the blocks etc.
            // and we pass that task to the [GenericBlockHeaderValidator]
            val validationResult = chainValidation(bctx, bcRid, minimalHeaders)
            if (validationResult.result != ValidationResult.Result.OK) {
                logger.error(
                        "Failing to anchor a block for blockchain ${bcRid.toHex()}. ${validationResult.message}"
                )
                return false
            }
        }
        return true
    }

    // TODO witness check
    // 1. Save time:
    //    When we are the primary, we are getting headers from our local machine, shouldn't need to check it (again).
    //    (But when we are copying finished anchor block from another node we actually should validate,
    //    but that's not here).
    // 2. Tech issues:
    //    The witness check is tricky b/c every chain we will validate must have its own instance of the
    //    [BlockHeaderValidator], specific for the chain in question (b/c we cannot be certain of what signers
    //    are relevant for the chain). ALSO!! We cannot just pick the latest configuration to get the signer list,
    //    but we must take the configuration for the height in question!
    // 3. In theory we cannot be certain about what [CryptoSystem] is used by the chain, so it should be taken from the
    //    config too.

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
        val myHeaderMap: MutableMap<Long, MinimalBlockHeaderInfo> = mutableMapOf()
        for (minimalHeader in minimalHeaders) {
            myHeaderMap[minimalHeader.headerHeight] = minimalHeader
        }

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
     * Add the height to the map
     */
    private fun updateChainHeightMap(
            bcRid: BlockchainRid,
            newInfo: MinimalBlockHeaderInfo,
            chainHeightMap: MutableMap<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>
    ): Boolean {
        val headers = chainHeightMap[bcRid]
        if (headers == null) {
            chainHeightMap[bcRid] = mutableSetOf(newInfo)
        } else {
            if (headers.all { header -> header.headerHeight != newInfo.headerHeight }) { // Rather primitive, but should be enough
                headers.add(newInfo)
            } else {
                logger.warn("Adding the same header twice, bc RID: ${bcRid.toHex()}, height ${newInfo.headerHeight}. New block: $newInfo")
                return false
            }
        }
        return true
    }

    /**
     * Checks a single operation for validity, which means go through the header and verify it.
     */
    private fun isOpValidAndFillTheMinimalHeaderMap(
            op: OpData,
            chainMinimalHeadersMap: MutableMap<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>
    ): Boolean {
        val anchorOpData = AnchorOpData.validateAndDecodeOpData(op) ?: return false

        val header: BlockHeaderData = anchorOpData.headerData
        val bcRid = BlockchainRid(header.getBlockchainRid())

        val newHeight = header.getHeight()
        if (newHeight < 0) { // Ok, pretty stupid check, but why not
            logger.error(
                    "Someone is trying to anchor a block for blockchain: " +
                            "${bcRid.toHex()} at height = $newHeight (which is impossible!). "
            )
            return false
        }

        val headerBlockRid =
                BlockRid(anchorOpData.blockRid) // Another way to get BlockRid is to calculate it from the header
        val headerPrevBlockRid = BlockRid(header.getPreviousBlockRid())
        val newBlockHeight = header.getHeight()

        if (!updateChainHeightMap(
                        bcRid,
                        MinimalBlockHeaderInfo(headerBlockRid, headerPrevBlockRid, newBlockHeight),
                        chainMinimalHeadersMap
                )) {
            return false
        }

        return true
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
            TempBlockInfo.fromBlock(block, bcRid)
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
            TempBlockInfo.fromBlock(block, bcRid)
        }
    }

    private fun buildArgs(vararg args: Pair<String, Gtv>): Gtv = gtv(*args)

    /**
     * Not really a domain object, just used to return some data
     */
    data class TempBlockInfo(
            val bcRid: BlockchainRid,
            val blockRid: BlockRid,
            val height: Long
    ) {
        companion object {
            fun fromBlock(
                    block: Gtv,
                    bcRid: BlockchainRid
            ): TempBlockInfo {
                val gtvDict = block.asDict()
                return TempBlockInfo(
                        bcRid,
                        BlockRid(gtvDict["block_rid"]!!.asByteArray()),
                        gtvDict["block_height"]!!.asInteger()
                )
            }
        }
    }
}
