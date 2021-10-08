package net.postchain.anchor
import mu.KLogging
import net.postchain.base.*
import net.postchain.base.data.BaseBlockHeaderValidator
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.base.icmf.IcmfPackage
import net.postchain.base.icmf.IcmfReceiver
import net.postchain.config.blockchain.*
import net.postchain.core.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.OpData
import java.lang.IllegalStateException

/**
 * When anchoring a block header we must fill the block of the anchoring BC with "__anchor_block_header" operations.
 */
class AnchorSpecialTxExtension : GTXSpecialTxExtension {

    private val _relevantOps = mutableSetOf<String>()

    private var myChainRid: BlockchainRid? = null // We must know the id of the anchor chain itself
    private var myChainIid: Long? = null // We must know the id of the anchor chain itself

    private var cs: CryptoSystem? = null

    private var blockchainConfigProvider: BlockchainConfigurationProvider? = null

    private var icmfReceiver: IcmfReceiver? = null // This is where we get the actual data to create operations

    private var blockQueries: BlockQueries? = null // This is for querying ourselves, i.e. the "anchor rell app"

    private var quickConfReader: HeightAwareConfigReader? = null // Will find all configuration settings we need

    // We will need one validator for each blockchain we are anchoring
    private val validationMap: Map<BlockchainRid, BlockHeaderValidator> = hashMapOf()

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    override fun getRelevantOps() = _relevantOps

    override fun init(
        module: GTXModule,
        blockchainRID: BlockchainRid,
        cs: CryptoSystem,
        storage: Storage?,
        confProvider: BlockchainConfigurationProvider
    ) {
        if (!_relevantOps.contains(OP_BLOCK_HEADER)) {
            _relevantOps.add(OP_BLOCK_HEADER)
        }

        myChainRid = blockchainRID // TODO: Olle: verify this is correct

        this.cs = cs

        blockchainConfigProvider = confProvider

        quickConfReader = QuickHeightAwareConfigReader(storage!!, confProvider)
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
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, blockchainRID: BlockchainRid): List<OpData> {
        val retList = ArrayList<OpData>()

        verifySameChainId(bctx, blockchainRID)
        val pipes = this.icmfReceiver!!.getNonEmptyPipesForListenerChain(blockchainRID) // Returns the pipes that has anchor chain as a listener

        // Extract all packages from all pipes
        for (pipe in pipes) {

            var debugCounter = 0  // Remove if this is disturbing
            // Loop all messages for the pipe
            while (!pipe.isEmpty()) {
                debugCounter++
                val icmfPackage = pipe.pull()!!
                val anchorOpData = buildOpData(icmfPackage)
                retList.add(anchorOpData)
            }
            // logger.debug("Pulled $debugCounter messages from pipeId: ${pipe.pipeId}")
        }
        return retList
    }

    /**
     * Transform to [IcmfPackage] to [OpData] put arguments in correct order
     *
     * @param icmfPackage is what we get from ICMF
     * @return is the [OpData] we can use to create a special TX.
     */
    fun buildOpData(icmfPackage: IcmfPackage): OpData {
        val gtvHeaderMsg = icmfPackage.blockHeader // We don't care about any messages, only the header
        val headerMsg = BlockHeaderDataFactory.buildFromGtv(gtvHeaderMsg) // Yes, a bit expensive going back and forth between GTV and Domain objects like this
        val witnessBytes: ByteArray = icmfPackage.witness.asByteArray()

        val gtvHeader: Gtv = headerMsg.toGtv()
        val gtvWitness = GtvByteArray(witnessBytes)

        return OpData(OP_BLOCK_HEADER, arrayOf(gtvHeader, gtvWitness))
    }

    /**
     * We loop all operations (to check if the block headers are ok)
     */
    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        return ops.all { isOpValid(it) }
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
     *
     * 1. General check:
     *    Initially we compare the height we see in the header with what we expect from our local table
     * 2. Witness check:
     *    The witness check is tricky b/c every chain we will validate must have its own instance of the
     *    [BlockHeaderValidator], specific for the chain in question (b/c we cannot be certain of what signers
     *    are relevant for the chain). ALSO!! We cannot just pick the latest configuration to get the signer list,
     *    but we must take the configuration for the height in question!
     *
     * Discussion1: managed mode only
     * Since anchoring is done locally, we will ask chain0 for the configurations. This means this will only work for
     * managed mode, and manual mode (which is only used for testing anyways) will simply not verify witness signatures
     * (based on discussion with Alex).
     *
     * Discussion2: crypto system
     * In theory we cannot be certain about what [CryptoSystem] is used by the chain, so it should be taken from the
     * config too.
     */
    fun isOpValid(op: OpData): Boolean {

        val anchorObj = AnchorOpDataObject.validateAndDecodeOpData(op)?: return false


        val header: BlockHeaderData = anchorObj.headerData
        val bcRid = BlockchainRid(header.gtvBlockchainRid.bytearray)

        /**
         * NOTE: We declare this as an inner function to access BC RID.
         *
         * @return the block RID at a certain height
         */
        fun getBlockRidAtHeight(height: Long): ByteArray? = getAnchoredBlockAtHeight(bcRid, height)?.blockRid?.data

        // ------------------
        // 1. General check:
        // ------------------
        val tmpBlockInfo = getLastAnchoredBlock(bcRid)
        if (tmpBlockInfo == null) {
            // ------------------
            // 1.a) We've never anchored any block for this chain before.
            // ------------------
            val newHeight = header.getHeight()
            if (newHeight == 0L) {
                logger.info("We are anchoring first block for blockchain ${bcRid.toShortHex()} (height = $newHeight).")
                return true
            } else if (newHeight > 0L) {
                // Not sure if we should expect the block height to begin from the beginning (=0)?
                // But better to allow anchoring to start in the middle this than to stop it I guess?
                logger.warn("We are anchoring first block for a previously unseen blockchain ${bcRid.toHex()}, " +
                        "starting at height = $newHeight. ")
                return true
            } else {
                // There's no doubt this is broken.
                logger.error("Someone is trying to anchor first block for a previously unseen blockchain: " +
                        "${bcRid.toHex()} at height = $newHeight (which is impossible!). ")
                return false
            }
        } else {
            // ------------------
            // 1.b) This is a known chain, let's verify things we know
            // ------------------

            var validator = validationMap[bcRid]!!
            /*
            if (validator == null) {
                // If we don't have a validator in the cache, we just build one
                // (but only if we are in managed mode, manual mode doesn't have chain0).
                // We cannot get the signers without first getting the configuration, so we need to call chain0 for that

                val blockQueries = withReadWriteConnection(storage, chain0) { ctx0 ->
                    val configuration = blockchainConfigProvider.getActiveBlocksConfiguration(ctx0, chain0)
                        ?:  null // Too bad, assume we are in manual mode!

                val configRaw =


                val blockHeaderValidator: BlockHeaderValidator = BaseBlockHeaderValidator(
                    this.cs!!, // This is not 100% kosher, since we must go into the configuration to really verify the CryptoSystem of this chain.
                    configData.blockSigMaker,
                    signers.toTypedArray()
                )
            }
             */

            val headerBlockRid = BlockRid(header.getMerkleRootHash())
            val headerPrevBlockRid = BlockRid(header.gtvPreviousBlockRid.bytearray)
            val expectedPrevBlockRid = tmpBlockInfo.blockRid
            val oldHeight: Long = tmpBlockInfo.height
            val expectedHeight =  oldHeight + 1
            val newBlockHeight = header.getHeight()


            // Use the validator Do the check
            val result = validator.basicValidationAgainstKnownBlocks(
                headerBlockRid,
                headerPrevBlockRid,
                newBlockHeight,
                expectedPrevBlockRid,
                expectedHeight,
                ::getBlockRidAtHeight // Using a locally defined function, and a closure here to use the bc RID
            )

            if (result.result != ValidationResult.Result.OK) {
                logger.error("Failing to anchor a block at height: $newBlockHeight " +
                    "(blockchain ${bcRid.toHex()}, old height $oldHeight). ${result.message}")
                return false
            }

            // ------------------
            // 2. Witness check:
            // ------------------
            if (isManagedMode()) {
                val witness = BaseBlockWitness.fromBytes(anchorObj.witness)
                // We must ask chain 0 for the configurations of the blockchain at the height we are at

                if (logger.isDebugEnabled) {
                    logger.debug("Witness check of block not possible for anchoring in manual mode.")
                }
                return true
            } else {
                if (logger.isDebugEnabled) {
                    logger.debug("Witness check of block not possible for anchoring in manual mode.")
                }
                return true // Do nothing for manual mode
            }
        }
    }

    fun isManagedMode(): Boolean {
        // TODO: Olle: Impl
        return false
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