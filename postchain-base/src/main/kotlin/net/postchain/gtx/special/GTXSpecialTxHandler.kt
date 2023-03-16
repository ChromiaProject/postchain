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
    private val opToExtension: Map<String, GTXSpecialTxExtension> = buildMap {
        for (x in extensions) {
            x.init(module, chainID, blockchainRID, cs)
            for (op in x.getRelevantOps()) {
                if (containsKey(op)) {
                    throw ProgrammerMistake("Overlapping op: $op")
                }
                put(op, x)
            }
        }
    }

    companion object : KLogging()

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
        val operations = (tx as GTXTransaction).gtxData.gtxBody.operations.map { it.toOpData() }

        // empty ops
        if (operations.isEmpty()) {
            logger.warn("Empty operation list is not allowed")
            return false
        }

        // __nop
        val nopIdx = operations.indexOfFirst { it.opName == GtxSpecNop.OP_NAME }
        if (nopIdx != -1 && nopIdx != operations.lastIndex) {
            logger.warn("${GtxSpecNop.OP_NAME} is allowed only as the last operation")
            return false
        }

        val extOps = operations
                .filter { it.opName != GtxSpecNop.OP_NAME }
                .groupBy { opToExtension[it.opName] }

        // unknown ops
        if (extOps.containsKey(null)) {
            logger.warn("Unknown operation detected: ${extOps[null]?.toTypedArray()?.contentToString()}")
            return false
        }

        // ext validation
        extOps.forEach { (ext, ops) ->
            if (ext != null && !ext.validateSpecialOperations(position, bctx, ops)) {
                logger.warn("Validation failed in special handler ${ext.javaClass.name}")
                return false
            }
        }

        return true
    }
}