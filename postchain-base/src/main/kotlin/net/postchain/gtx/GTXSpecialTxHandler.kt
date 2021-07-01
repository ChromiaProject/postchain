// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.base.BlockchainRid
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.core.ProgrammerMistake
import net.postchain.core.Transaction
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType
import kotlin.math.log


interface GTXSpecialTxExtension {
    fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem)
    fun getRelevantOps(): Set<String>
    fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean
    fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData>
    fun validateSpecialOperations(position: SpecialTransactionPosition,
                                  bctx: BlockEContext, ops: List<OpData>): Boolean
}

class GTXAutoSpecialTxExtension: GTXSpecialTxExtension {
    var wantBegin: Boolean = false
    var wantEnd: Boolean = false

    private val _relevantOps = mutableSetOf<String>()

    companion object {
        const val OP_BEGIN_BLOCK = "__begin_block"
        const val OP_END_BLOCK = "__end_block"
    }

    override fun getRelevantOps() = _relevantOps

    override fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem) {
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

    fun validateOp( bctx: BlockEContext, op: OpData, requiredOpName: String): Boolean {
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

open class GTXSpecialTxHandler(val module: GTXModule,
                               val blockchainRID: BlockchainRid,
                               val cs: CryptoSystem,
                               val factory: GTXTransactionFactory
) : SpecialTransactionHandler {

    private val extensions: List<GTXSpecialTxExtension> = module.getSpecialTxExtensions()

    companion object : KLogging()

    init {
        val opSet = mutableSetOf<String>()
        for (x in extensions) {
            x.init(module, blockchainRID, cs)
            for (op in x.getRelevantOps()) {
                if (op in opSet) throw ProgrammerMistake("Overlapping op: $op")
                opSet.add(op)
            }
        }
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return extensions.any { it.needsSpecialTransaction(position) }
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction {
        val b = GTXDataBuilder(blockchainRID, arrayOf(), cs)
        for (x in extensions) {
            if (x.needsSpecialTransaction(position)) {
                for (o in x.createSpecialOperations(position, bctx)) {
                    b.addOperation(o.opName, o.args)
                }
            }
        }
        return factory.decodeTransaction(b.serialize())
    }

    override fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, ectx: BlockEContext): Boolean {
        val gtxTransaction = tx as GTXTransaction
        val gtxData = gtxTransaction.gtxData
        val operations = gtxData.transactionBodyData.operations
        if (operations.isEmpty()) return false

        var idx = 0

        for (ext in extensions) {
            if (ext.needsSpecialTransaction(position)) {
                val rops = ext.getRelevantOps()
                val selectesOps = mutableListOf<OpData>()
                // select relevant ops
                while (operations[idx].opName in rops) {
                    selectesOps.add(operations[idx])
                    idx += 1
                    if (idx >= operations.size) break
                }
                if (!ext.validateSpecialOperations(position, ectx, selectesOps)) {
                    logger.warn("Validation failed in special handler ${ext.javaClass.name}")
                    return false
                }
            }
        }

        return if (idx == operations.size) {
            true
        } else if (idx == operations.size - 1) {
            if (operations[idx].opName == "nop") { // nop is allowed as last operation
                true
            } else {
                logger.warn("Unprocessed special op: ${operations[idx].opName}")
                false
            }
        } else {
            val opNames = operations.slice(IntRange(idx, operations.size)).map { it.opName }
                .joinToString()
            logger.warn("Too many operations in special transaction: $opNames")
            false
        }
    }
}