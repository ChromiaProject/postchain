package net.postchain.anchor
import mu.KLogging
import net.postchain.base.BaseBlockWitness
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.base.icmf.IcmfReceiver
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainRid
import net.postchain.gtv.GtvByteArray
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.OpData
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.Gtv
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
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, blockchainRID: BlockchainRid): List<OpData> {
        val retList = ArrayList<OpData>()

        verifyChainId(bctx, blockchainRID)
        val pipes = this.icmfReceiver!!.getPipesForChain(blockchainRID)

        // Loop all pipes
        for (pipe in pipes) {

            var debugCounter = 0  // Remove if this is disturbing
            // Loop all messages for the pipe
            while (!pipe.isEmpty()) {
                debugCounter++
                val icmfPackage = pipe.pull()!!
                val gtvHeaderMsg = icmfPackage.blockHeader // We don't care about any messages, only the header
                val headerMsg = BlockHeaderDataFactory.buildFromGtv(gtvHeaderMsg) // Yes, a bit expensive going back and forth between GTV and Domain objects like this

                // Get what we need
                val gtvBcRid = GtvByteArray(headerMsg.getBlockchainRid())
                val gtvHeight = GtvInteger(bctx.height)
                val gtvHeader: Gtv = headerMsg.toGtv()

                val opData = OpData(OP_BLOCK_HEADER, arrayOf(gtvHeight, gtvBcRid, gtvHeader))
                retList.add(opData)
            }
            logger.debug("Pulled $debugCounter messages from pipeId: ${pipe.pipeId}") // TODO: Olle: Remove this before prod
        }
        return retList
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
    private fun verifyChainId(bctx: BlockEContext, blockchainRID: BlockchainRid ) {
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
        if (OP_BLOCK_HEADER != op.opName) {
            logger.info("Invalid spec operation: Expected op name $OP_BLOCK_HEADER got ${op.opName}.")
            return false
        }

        if (op.args.size != 2) {
            logger.info("Invalid spec operation: Expected 1 arg but got ${op.args.size}.")
            return false
        }

        val gtvHeader = op.args[0]
        val header = BlockHeaderDataFactory.buildFromGtv(gtvHeader)

        val gtvWitness = op.args[1]
        val rawWitness = gtvWitness.asByteArray()
        val witness = BaseBlockWitness.fromBytes(rawWitness)

        // TODO: Olle, Must verify signatures, but then not sure what to check for?


        return true
    }


}