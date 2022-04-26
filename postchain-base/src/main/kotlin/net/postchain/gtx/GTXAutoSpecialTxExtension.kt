package net.postchain.gtx

import net.postchain.crypto.CryptoSystem
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType


/**
 * This extension adds "begin" and "end" to the block, which how a block should be constructed.
 *
 * Note: This extension is called "auto" because it's always needed (so technically not extending the protocol,
 * but part of it, but the extension mechanism is the cleanest way to add extra TXs).
 */
class GTXAutoSpecialTxExtension: GTXSpecialTxExtension {
    var wantBegin: Boolean = false
    var wantEnd: Boolean = false

    private val _relevantOps = mutableSetOf<String>()

    companion object {
        const val OP_BEGIN_BLOCK = "__begin_block"
        const val OP_END_BLOCK = "__end_block"
    }

    override fun getRelevantOps() = _relevantOps

    /**
     * (Alex:) We only add the "__begin_.." and "__end.." if they are used by the Rell programmer writing the module,
     * so we must check the module for these operations before we know if they are relevant.
     */
    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
        val ops = module.getOperations()
        if (OP_BEGIN_BLOCK in ops) {
            wantBegin = true
            _relevantOps.add(OP_BEGIN_BLOCK)
        }
        if (OP_END_BLOCK in ops) {
            wantEnd = true
            _relevantOps.add(OP_END_BLOCK)
        }
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return when (position) {
            SpecialTransactionPosition.Begin -> wantBegin
            SpecialTransactionPosition.End -> wantEnd
        }
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        val op = if (position == SpecialTransactionPosition.Begin)
            OP_BEGIN_BLOCK else OP_END_BLOCK
        return listOf(OpData(op, arrayOf(GtvInteger(bctx.height))))
    }

    private fun validateOp(bctx: BlockEContext, op: OpData, requiredOpName: String): Boolean {
        if (op.opName != requiredOpName) return false

        if (op.args.size != 1) return false
        val arg = op.args[0]
        if (arg.type !== GtvType.INTEGER) return false
        if (arg.asInteger() != bctx.height) return false
        return true
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (position == SpecialTransactionPosition.Begin) {
            if (wantBegin) {
                if (ops.size != 1) return false
                return validateOp(bctx, ops[0], OP_BEGIN_BLOCK)
            } else {
                return ops.isEmpty()
            }
        } else {
            if (wantEnd) {
                if (ops.size != 1) return false
                return validateOp(bctx, ops[0], OP_END_BLOCK)
            } else {
                return ops.isEmpty()
            }
        }
    }
}