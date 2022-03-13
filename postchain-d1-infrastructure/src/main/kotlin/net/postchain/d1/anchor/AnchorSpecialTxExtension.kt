package net.postchain.d1.anchor
import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.GenericBlockHeaderValidator
import net.postchain.base.data.MinimalBlockHeaderInfo
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.d1.icmf.IcmfPipe
import net.postchain.d1.icmf.IcmfReceiver
import net.postchain.config.blockchain.*
import net.postchain.core.*
import net.postchain.d1.icmf.ClusterAnchorRoutingRule
import net.postchain.d1.icmf.IcmfPacket
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.OpData
import java.lang.IllegalStateException

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
class AnchorSpecialTxExtension : GTXSpecialTxExtension {

    private val _relevantOps = setOf(OP_BLOCK_HEADER)

    private var myChainRid: BlockchainRid? = null // We must know the id of the anchor chain itself
    private var myChainIid: Long? = null // We must know the id of the anchor chain itself

    private var icmfReceiver: IcmfReceiver? = null // This is where we get the actual data to create operations

    private var blockQueries: BlockQueries? = null // This is for querying ourselves, i.e. the "anchor rell app"

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    override fun getRelevantOps() = _relevantOps

    override fun init(
        module: GTXModule,
        blockchainRID: BlockchainRid,
        cs: CryptoSystem
    ) {
        myChainRid = blockchainRID
    }

    /**
     * Not part of the usual init
     */
    fun setBlockQueries(bq: BlockQueries) {
        blockQueries = bq
    }

    /**
     * Asked Alex, and he said we always use "begin" for special TX (unless we are wrapping up something)
     * so we only add them here (if we have any).
     */
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return when (position) {
            SpecialTransactionPosition.Begin -> true
            SpecialTransactionPosition.End -> false
        }
    }

    /**
     * For Anchor chain we simply pull all the messages from all the ICMF pipes and create operations.
     *
     * Since the Extension framework expects us to add a TX before and/or after the main data of a block,
     * we create ONE BIG tx with all operations in it (for the "before" position).
     * (In an anchor chain there will be no "normal" transactions, only this one big "before" special TX)
     *
     * @param position will always be "begin", we don't care about it here
     * @param btcx is the context of the anchor chain (but without BC RID)
     * @param blockchainRID is the BC RID of the anchor chain (or something is wrong)
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val retList = mutableListOf<OpData>()

        //verifySameChainId(bctx, blockchainRID)
        val pipes = this.icmfReceiver!!.getRelevantPipes()

        // Extract all packages from all pipes
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
            pipe: IcmfPipe,
            retList: MutableList<OpData>,
            bctx: BlockEContext
    ) {
        if (pipe.id.routingRule != ClusterAnchorRoutingRule) return
        var counter = 0
        val blockchainRid = BlockchainRid(pipe.id.key.asByteArray())
        var currentHeight: Long = getLastAnchoredHeight(blockchainRid)
        while (pipe.mightHaveNewPackets()) {
            val icmfPackage = pipe.fetchNext(GtvInteger(currentHeight))
            if (icmfPackage != null) {
                retList.add(buildOpData(icmfPackage))
                pipe.markTaken(icmfPackage.currentPointer, bctx)
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

    private fun getLastAnchoredHeight(blockchainRID: BlockchainRid): Long {
        val tmpBlockInfo = getLastAnchoredBlock(blockchainRID)
        return if (tmpBlockInfo == null) {
            -1
        } else {
            tmpBlockInfo.height
        }
    }

    /**
     * Transform to [IcmfPackage] to [OpData] put arguments in correct order
     *
     * @param icmfPackage is what we get from ICMF
     * @return is the [OpData] we can use to create a special TX.
     */
    fun buildOpData(icmfPackage: IcmfPacket): OpData {
        val gtvHeaderMsg = icmfPackage.blockHeader // We don't care about any messages, only the header
        val headerMsg = BlockHeaderDataFactory.buildFromGtv(gtvHeaderMsg) // Yes, a bit expensive going back and forth between GTV and Domain objects like this
        val witnessBytes: ByteArray = icmfPackage.witness.asByteArray()

        val gtvBlockRid: Gtv = icmfPackage.blockRid
        val gtvHeader: Gtv = headerMsg.toGtv()
        val gtvWitness = GtvByteArray(witnessBytes)

        return OpData(OP_BLOCK_HEADER, arrayOf(gtvBlockRid, gtvHeader, gtvWitness))
    }

    /**
     * We look at the content of all operations (to check if the block headers are ok and nothing is missing)
     */
    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        val chainHeadersMap = mutableMapOf<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>()
        val valid = ops.all { isOpValidAndFillTheMinimalHeaderMap(it, chainHeadersMap) }
        if (!valid) {
            return false // Usually meaning the actual format of a header is broken
        }

        // Go through it chain by chain
        for (bcRid in chainHeadersMap.keys) {
            // Each chain must be validated by itself b/c we must now look for gaps in the blocks etc
            // and we pass that task to the [GenericBlockHeaderValidator]
            val minimalHeaders = chainHeadersMap[bcRid]
            if (minimalHeaders != null) {
                val valid = chainValidation(bcRid, minimalHeaders)
                if (valid.result != ValidationResult.Result.OK) {
                    logger.error("Failing to anchor a block for blockchain ${bcRid.toHex()}. ${valid.message}")
                    return false
                }
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
     * Discussion1: crypto system
     *   In theory we cannot be certain about what [CryptoSystem] is used by the chain, so it should be taken from the
     *   config too.
     *
     * Discussion2: Why no witness check here?
     *    1. Save time:
     *      When we are the primary, we are getting headers from our local machine, shouldn't need to check it (again).
     *      (But when we are copying finished anchor block from another node we actually should validate,
     *      but that's not here).
     *    2. Tech issues:
     *      The witness check is tricky b/c every chain we will validate must have its own instance of the
     *      [BlockHeaderValidator], specific for the chain in question (b/c we cannot be certain of what signers
     *      are relevant for the chain). ALSO!! We cannot just pick the latest configuration to get the signer list,
     *      but we must take the configuration for the height in question!
     *
     * @param bcRid is the chain we intend to validate
     * @param minimalHeaders is a very small data set for each header (that we use for basic validation)
     * @return the result of the validation
     */
    private fun chainValidation(
        bcRid: BlockchainRid,
        minimalHeaders: MutableSet<MinimalBlockHeaderInfo>
    ): ValidationResult {

        /**
         * NOTE: We declare this as an inner function to access BC RID.
         *
         * @return the block RID at a certain height
         */
        fun getBlockRidAtHeight(height: Long): ByteArray? = getAnchoredBlockAtHeight(bcRid, height)?.blockRid?.data

        // Restructure to the format the Validator needs
        val myMap: MutableMap<Long, MinimalBlockHeaderInfo> = mutableMapOf()
        for (minimalHeader in minimalHeaders) {
            myMap[minimalHeader.headerHeight] = minimalHeader
        }

        // Get the expected data
        val expected = getExpectedData(bcRid)

        // Run the validator
        return GenericBlockHeaderValidator.multiValidationAgainstKnownBlocks(
            bcRid,
            myMap,
            expected,
            ::getBlockRidAtHeight // Using a locally defined function, and a closure here to use the bc RID
        )
    }

    /**
     * @return the data we expect to find, fetched from Anchor module's own tables
     */
    private fun getExpectedData(bcRid: BlockchainRid): MinimalBlockHeaderInfo? {
        val tmpBlockInfo = getLastAnchoredBlock(bcRid)
        return if (tmpBlockInfo == null) {
            // We've never anchored any block for this chain before.
            null
        } else {
            // We found something, return it
            MinimalBlockHeaderInfo(tmpBlockInfo.blockRid, null, tmpBlockInfo.height) // Don't care about the prev block here
        }
    }

    /**
     * Add the height to the map
     */
    private fun updateChainHeightMap(
        bcRid: BlockchainRid,
        newInfo: MinimalBlockHeaderInfo,
        chainHeightMap: MutableMap<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>
    ) {
        var headers = chainHeightMap[bcRid]
        if (headers == null) {
            chainHeightMap[bcRid] = mutableSetOf(newInfo)
        } else {
            if (headers.all{ header -> header.headerHeight != newInfo.headerHeight }) { // Rather primitive, but should be enough
                headers.add(newInfo)
            } else {
                ProgrammerMistake("Adding the same header twice, bc RID: ${bcRid.toHex()}, height ${newInfo.headerHeight}. New block: $newInfo")
            }
        }
    }

    // ------------------------ PUBLIC NON-INHERITED ----------------

    /**
     * This must have been set before first time we call [createSpecialOperations()]
     */
    fun useIcmfReceiver(ir: IcmfReceiver) {
        if (icmfReceiver == null) {
            icmfReceiver = ir
        } else {
            logger.info("Adding a IcmfReceiver when we have one.")
        }
    }

    // ------------------------ PRIVATE ----------------

    /**
     * Save the chainID coming from [BlockEContext] into local state variable (myChainIid)
     * and verify it doesn't change.
     */
    private fun verifySameChainId(bctx: BlockEContext, blockchainRID: BlockchainRid ) {
        if (this.myChainIid == null) {
            this.myChainIid = bctx.chainID
        } else {
            if (this.myChainIid != bctx.chainID) { // Possibly useless check, but I'm paranoid
                throw IllegalStateException("Did anchor chain change chainID? Now: ${bctx.chainID}, before: $myChainIid")
            }
        }
        if (this.myChainRid == null) {
            this.myChainRid = blockchainRID
        } else {
            if (this.myChainRid != blockchainRID) { // Possibly useless check, but I'm paranoid
                throw IllegalStateException("Did anchor chain change chain RID? Now: ${blockchainRID}, before: $myChainRid")
            }
        }
    }

    /**
     * Checks a single operation for validity, which means go through the header and verify it.
     */
    private fun isOpValidAndFillTheMinimalHeaderMap(
        op: OpData,
        chainMinimalHeadersMap: MutableMap<BlockchainRid, MutableSet<MinimalBlockHeaderInfo>>
    ): Boolean {

        val anchorObj = AnchorOpDataObject.validateAndDecodeOpData(op)?: return false

        val header: BlockHeaderData = anchorObj.headerData
        val bcRid = BlockchainRid(header.getBlockchainRid())

        val newHeight = header.getHeight()
        if (newHeight < 0) { // Ok, pretty stupid check, but why not
            logger.error("Someone is trying to anchor a block for blockchain: " +
                    "${bcRid.toHex()} at height = $newHeight (which is impossible!). ")
            return false
        }

        val headerBlockRid = BlockRid(anchorObj.blockRid) // Another way to get BlockRid is to calculate it from the header
        val headerPrevBlockRid = BlockRid(header.getPreviousBlockRid())
        val newBlockHeight = header.getHeight()

        updateChainHeightMap(
            bcRid,
            MinimalBlockHeaderInfo(headerBlockRid, headerPrevBlockRid, newBlockHeight),
            chainMinimalHeadersMap)

        return true

    }

    /**
     * Ask the Anchor Module for last anchored block
     *
     * @param bcRid is the chain we are interested in
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getLastAnchoredBlock(bcRid: BlockchainRid): TempBlockInfo? {

        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
            Pair("blockchainRid", gtv(bcRidByteArr))
        )
        val block = blockQueries!!.query("get_last_anchored_block", args).get()

        return if (block == GtvNull) {
            null
        } else {
            buildReply(block, bcRid)
        }
    }

    /**
     * Ask the Anchor Module for anchored block at height
     *
     * @param bcRid is the chain we are interested in
     * @param height is the block height we want to look at
     * @return the block info for the last anchored block, or nothing if not found
     */
    private fun getAnchoredBlockAtHeight(bcRid: BlockchainRid, height: Long): TempBlockInfo? {

        val bcRidByteArr = bcRid.data // We're sending the RID as bytes, not as a string
        val args = buildArgs(
                Pair("blockchainRid", gtv(bcRidByteArr)),
                Pair("height", gtv(height))
            )
        val block = blockQueries!!.query("get_anchored_block_at_height", args).get()
        return if (block == GtvNull) {
            null
        } else {
            buildReply(block, bcRid)
        }
    }

    private fun buildReply(
        block: Gtv,
        bcRid: BlockchainRid
    ): TempBlockInfo {
        val gtvDict = block.asDict()
        val blockRidHex = gtvDict["block_rid"]!!.asString()
        val bRid = BlockRid.buildFromHex(blockRidHex)
        return TempBlockInfo(
            bcRid,
            bRid,
            gtvDict["block_height"]!!.asInteger(),
            gtvDict["status"]!!.asInteger()
        )
    }


    private fun buildArgs(vararg args: Pair<String, Gtv>): Gtv {
        return GtvFactory.gtv(*args)
    }
}


/**
 * Not really a domain object, just used to return some data
 */
data class TempBlockInfo(
    val bcRid: BlockchainRid,
    val blockRid: BlockRid,
    val height: Long,
    val status: Long) {

}