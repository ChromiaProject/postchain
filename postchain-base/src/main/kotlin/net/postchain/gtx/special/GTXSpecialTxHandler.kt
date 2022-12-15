// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.special

import mu.KLogging
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockEContext
import net.postchain.core.Transaction
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxSpecNop
import net.postchain.gtx.data.OpData

/**
 * In this case "Handler" means we:
 *
 * - can find out if we need a special tx, and
 * - can create a special tx, and
 * - can validate a special tx.
 *
 * Special transactions are usually created by a [GTXSpecialTxExtension], which makes this extendable.
 */
open class GTXSpecialTxHandler(val module: GTXModule,
                               val chainID: Long,
                               val blockchainRID: BlockchainRid,
                               val cs: CryptoSystem,
                               val factory: GTXTransactionFactory
) : SpecialTransactionHandler {

    private val extensions: List<GTXSpecialTxExtension> = module.getSpecialTxExtensions()

    companion object : KLogging()

    init {
        val opSet = mutableSetOf<String>()
        for (x in extensions) {
            x.init(module, chainID, blockchainRID, cs)
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
        val b = GtxBuilder(blockchainRID, listOf(), cs)
        for (x in extensions) {
            if (x.needsSpecialTransaction(position)) {
                for (o in x.createSpecialOperations(position, bctx)) {
                    b.addOperation(o.opName, *o.args)
                }
            }
        }
        if (b.isEmpty()) {
            // no extension emitted an operation - add "__nop" (same as "nop" but for spec tx)
            b.addOperation(GtxSpecNop.OP_NAME, GtvFactory.gtv(cs.getRandomBytes(32)))
        }
        return factory.decodeTransaction(b.finish().buildGtx().encode())
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
     * @param bctx
     * @return true if all special operations of all extensions valid
     */
    override fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, bctx: BlockEContext): Boolean {
        val gtxTransaction = tx as GTXTransaction
        val gtxData = gtxTransaction.gtxData
        val operations = gtxData.gtxBody.operations
        if (operations.isEmpty()) return false

        var idx = 0

        for (ext in extensions) {
            if (ext.needsSpecialTransaction(position)) {
                val rops = ext.getRelevantOps()
                val selectesOps = mutableListOf<OpData>()
                // select relevant ops
                while (operations[idx].opName in rops) {
                    selectesOps.add(operations[idx].toOpData())
                    idx += 1
                    if (idx >= operations.size) break
                }
                if (!ext.validateSpecialOperations(position, bctx, selectesOps)) {
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