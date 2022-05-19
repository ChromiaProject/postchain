package net.postchain.el2

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtx.*

const val OP_ETH_BLOCK = "__eth_block"

class EL2SpecialTxExtension : GTXSpecialTxExtension {
    var needL2Tnx: Boolean = false
    private val rops = setOf(OP_ETH_BLOCK)
    override fun getRelevantOps() = rops

    private lateinit var proc: EventProcessor

    fun useEventProcessor(processor: EventProcessor) {
        proc = processor
    }

    override fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        if (module.getOperations().contains(OP_ETH_BLOCK)) {
            needL2Tnx = true
        }
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return when (position) {
            SpecialTransactionPosition.Begin -> needL2Tnx
            SpecialTransactionPosition.End -> false
        }
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (position == SpecialTransactionPosition.Begin) {
            val data = proc.getEventData()
            return data.map { OpData(OP_ETH_BLOCK, it) }
        }
        return listOf()
    }


    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (position == SpecialTransactionPosition.Begin) {
            return proc.isValidEventData(ops.toTypedArray())
        }
        return ops.isEmpty()
    }
}