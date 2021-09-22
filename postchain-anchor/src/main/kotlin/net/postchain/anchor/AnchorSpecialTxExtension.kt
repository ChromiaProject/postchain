package net.postchain.anchor
import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.base.icmf.IcmfPackage
import net.postchain.base.icmf.IcmfReceiver
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
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

    private var icmfReceiver: IcmfReceiver? = null // This is where we get the actual data to create operations

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__anchor_block_header"
    }

    override fun getRelevantOps() = _relevantOps

    override fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        if (!_relevantOps.contains(OP_BLOCK_HEADER)) {
            _relevantOps.add(OP_BLOCK_HEADER)
        }
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
     */
    fun isOpValid(op: OpData): Boolean {

        val anchorObj = AnchorOpDataObject.validateAndDecodeOpData(op)?: return false

        val witness = BaseBlockWitness.fromBytes(anchorObj.witness)

        // TODO: Olle, Must verify signatures, but then not sure what to check for?

        return true
    }


}