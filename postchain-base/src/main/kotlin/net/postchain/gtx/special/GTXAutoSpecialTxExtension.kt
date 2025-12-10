package net.postchain.gtx.special

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.OpData

/**
 * This extension adds "begin" and "end" to the block, which how a block should be constructed.
 *
 * Note: This extension is called "auto" because it's always needed (so technically not extending the protocol,
 * but part of it, but the extension mechanism is the cleanest way to add extra TXs).
 */
class GTXAutoSpecialTxExtension : GTXNonSkippingSpecialTxExtension {
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

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
        if (position == SpecialTransactionPosition.Begin) {
            if (wantBegin) {
                if (ops.size != 1) throw UserMistake("missing $OP_BEGIN_BLOCK")
                validateOp(bctx, ops[0], OP_BEGIN_BLOCK)
            } else {
                if (ops.isNotEmpty()) throw UserMistake("Got unexpected operations")
            }
        } else {
            if (wantEnd) {
                if (ops.size != 1) throw UserMistake("missing $OP_END_BLOCK")
                validateOp(bctx, ops[0], OP_END_BLOCK)
            } else {
                if (ops.isNotEmpty()) throw UserMistake("Got unexpected operations")
            }
        }
        return true
    }

    private fun validateOp(bctx: BlockEContext, op: OpData, requiredOpName: String) {
        if (op.opName != requiredOpName) throw UserMistake("Wrong operation name")

        if (op.args.size != 1) throw UserMistake("needs 1 argument, got ${op.args.size}")
        val arg = op.args[0]
        if (arg.type !== GtvType.INTEGER) throw UserMistake("needs 1 integer argument")
        if (arg.asInteger() != bctx.height) throw UserMistake("${arg.asInteger()} != ${bctx.height}")
    }

    override fun isAllowedToSkipSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): Boolean =
            position == SpecialTransactionPosition.Begin && !wantBegin || position == SpecialTransactionPosition.End && !wantEnd
}
