// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.base.BlockchainRid
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.core.Transaction
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType

const val OP_BEGIN_BLOCK = "__begin_block"
const val OP_END_BLOCK = "__end_block"

// NOTE: we need to refactor this if we want to make it subclass-able
open class GTXSpecialTxHandler(val module: GTXModule,
                          val blockchainRID: BlockchainRid,
                          val cs: CryptoSystem,
                          val factory: GTXTransactionFactory
) : SpecialTransactionHandler {

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        val op = if (position == SpecialTransactionPosition.Begin)
            OP_BEGIN_BLOCK else OP_END_BLOCK
        return module.getOperations().contains(op)
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction {
        val b = GTXDataBuilder(blockchainRID, arrayOf(), cs)
        val op = if (position == SpecialTransactionPosition.Begin)
            OP_BEGIN_BLOCK else OP_END_BLOCK
        b.addOperation(op, arrayOf(GtvInteger(bctx.height)))
        return factory.decodeTransaction(b.serialize())
    }

    override fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, ectx: BlockEContext): Boolean {
        val gtxTransaction = tx as GTXTransaction
        val gtxData = gtxTransaction.gtxData
        if (gtxData.transactionBodyData.operations.isEmpty()) return false
        val op0 = gtxData.transactionBodyData.operations[0]
        if (op0.opName != if (position == SpecialTransactionPosition.Begin)
                    OP_BEGIN_BLOCK
                else OP_END_BLOCK) return false
        if (op0.args.size != 1) return false
        val arg = op0.args[0]
        if (arg.type !== GtvType.INTEGER) return false
        if (arg.asInteger() != ectx.height) return false

        // allow single "nop" after begin_block
        if (gtxData.transactionBodyData.operations.size > 1) {
            val op1 = gtxData.transactionBodyData.operations[1]
            if (op1.opName != "nop") return false
            if (gtxData.transactionBodyData.operations.size > 2) return false
        }

        return true
    }
}