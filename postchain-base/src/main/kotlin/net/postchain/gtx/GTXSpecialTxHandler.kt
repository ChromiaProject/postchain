// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.base.CryptoSystem
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake
import net.postchain.core.Transaction
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType

/**
 * Holds various info regarding special TXs used by an extension, when a Spec TX is needed and how to create Spec TX etc.
 *
 * NOTE: Remember that the Sync Infra Extension is just a part of many extension interfaces working together
 * (examples: BBB Ext and Sync Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 */
interface GTXSpecialTxExtension {
    fun init(module: GTXModule, blockchainRID: BlockchainRid, cs: CryptoSystem)

    /**
     * @return the names of all special operations relevant for this extension
     */
    fun getRelevantOps(): Set<String>

    /**
     * @param position is position in the block, either "begin" or "end"
     * @return true if this position needs a special transaction.
     */
    fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean

    /**
     * The parameters below should be enough to find the data needed to create a special operation:
     *
     * @param position is position in the block, either "begin" or "end"
     * @param bctx
     * @param blockchainRID is the alternative identifier of the chain (we can get chainIid from the [BlockEContext])
     * @return all new operations created
     */
    fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, blockchainRID: BlockchainRid): List<OpData>

    /**
     * @return true if the list of operations are considered valid
     */
    fun validateSpecialOperations(position: SpecialTransactionPosition,
                                  bctx: BlockEContext, ops: List<OpData>): Boolean
}

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

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, blockchainRID: BlockchainRid): List<OpData> {
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

/**
 *
 */
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
                for (o in x.createSpecialOperations(position, bctx, blockchainRID)) {
                    b.addOperation(o.opName, o.args)
                }
            }
        }
        if (b.operations.isEmpty()) {
            // no extension emitted an operation - add "__nop" (same as "nop" but for spec tx)
            b.addOperation(GtxSpecNop.OP_NAME, arrayOf(GtvFactory.gtv(cs.getRandomBytes(32))))
        }
        return factory.decodeTransaction(b.serialize())
    }

    /**
     * The goal of this method is to call "validateSpecialOperations()" on all extensions we have.
     *
     * NOTE: For the logic below to work no two extensions can have operations with the same name. If they do we
     *       might use the wrong extension to validate an operation.
     *
     * @param position is the position we are investigating
     * @param tx is the [Transaction] we are investigating (must already have been created at an earlier stage).
     *           This tx holds all operations from all extensions, so it can be very big (in case of Anchoring chain at least)
     * @param ectx
     * @param true if all special operations of all extensions valid
     */
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
            if (operations[idx].opName == GtxSpecNop.OP_NAME) { // __nop is allowed as last operation
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