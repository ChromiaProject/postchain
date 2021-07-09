package net.postchain.base.icmf

import mu.KLogging
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainRid
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.OpData
import net.postchain.gtv.GtvInteger

/**
 *
 */
class IcmfSpecialTxExtension : GTXSpecialTxExtension {

    private val _relevantOps = mutableSetOf<String>()

    companion object : KLogging() {
        const val OP_BLOCK_HEADER = "__icmf_block_header"
    }

    override fun getRelevantOps() = _relevantOps

    override fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        if (!_relevantOps.contains(OP_BLOCK_HEADER)) {
            _relevantOps.add(OP_BLOCK_HEADER)
        }
    }

    /**
     * For ICMF we always must add the block header as the last transaction
     */
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return when (position) {
            SpecialTransactionPosition.Begin -> false // TODO: (Olle) not sure about this, how do we get events into here?
            SpecialTransactionPosition.End -> true
        }
    }

    /**
     * The operation to create is "block header"
     */
    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        return listOf(OpData(OP_BLOCK_HEADER, arrayOf(GtvInteger(bctx.height)))) // TODO: (Olle) do we need the entire header?
    }


    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        // TODO: Olle: Should we do something here?
        /*
        if (position == SpecialTransactionPosition.Begin) {
            return proc.isValidEventData(ops.toTypedArray())
        }
        return ops.isEmpty()
         */
    }
}