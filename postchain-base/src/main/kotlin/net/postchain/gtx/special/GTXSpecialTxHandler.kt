// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.special

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.FaultyExtensionException
import net.postchain.core.Transaction
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.GtxSpecNop
import net.postchain.logging.TRANSACTION_RID_TAG

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

    companion object : KLogging() {
        const val VALIDATE_SPECIAL_TRANSACTION = "validateSpecialTransaction() -- {}, position: {}"
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return extensions.any { it.needsSpecialTransaction(position) }
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction {
        val ops = mutableListOf<GtxOp>()
        for (x in extensions) {
            if (x.needsSpecialTransaction(position)) {
                try {
                    for (o in x.createSpecialOperations(position, bctx)) {
                        ops.add(GtxOp(o.opName, *o.args))
                    }
                } catch (e: Exception) {
                    throw FaultyExtensionException("Unexpected exception when creating special transaction at position: $position", e)
                }
            }
        }
        if (ops.isEmpty()) {
            // no extension emitted an operation - add "__nop" (same as "nop" but for spec tx)
            ops.add(GtxOp(GtxSpecNop.OP_NAME, GtvFactory.gtv(cs.getRandomBytes(32))))
        }
        val tx = Gtx(GtxBody(blockchainRID, ops, listOf()), listOf())
        return factory.decodeTransaction(tx.encode())
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
        withLoggingContext(TRANSACTION_RID_TAG to tx.getRID().toHex()) {
            logger.trace(VALIDATE_SPECIAL_TRANSACTION, "Begin", position)

            val operations = (tx as GTXTransaction).gtxData.gtxBody.operations.map { it.asOpData() }

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
                try {
                    if (ext != null && !ext.needsSpecialTransaction(position)) {
                        logger.warn("Special handler ${ext.javaClass.name} does not need special transaction at position: $position")
                        return false
                    }
                    if (ext != null && !ext.validateSpecialOperations(position, bctx, ops)) {
                        logger.warn("Validation failed in special handler ${ext.javaClass.name}")
                        return false
                    }
                } catch (e: Exception) {
                    // Extensions should not throw when validating
                    throw FaultyExtensionException("Unexpected exception while validating transaction at position: $position", e)
                }
            }
            extensions.filterIsInstance<GTXNonSkippingSpecialTxExtension>()
                    .filterNot { extOps.keys.contains(it) }
                    .forEach { skippedExtension ->
                        if (!skippedExtension.isAllowedToSkipSpecialOperations(position, bctx)) {
                            logger.warn("Skipping special operations is not allowed by handler ${skippedExtension.javaClass.name}")
                            return false
                        }
                    }
            logger.trace(VALIDATE_SPECIAL_TRANSACTION, "End", position)
        }
        return true
    }

    override fun isAllowedToSkipSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Boolean {
        extensions.filterIsInstance<GTXNonSkippingSpecialTxExtension>().forEach { extension ->
            if (!extension.isAllowedToSkipSpecialOperations(position, bctx)) {
                logger.warn("Skipping special transaction at position: $position is not allowed by handler ${extension.javaClass.name}")
                return false
            }
        }
        return true
    }

    override fun shouldAffectBlockBuilding(): Boolean =
            extensions.filterIsInstance<GTXBlockBuildingAffectingSpecialTxExtension>().isNotEmpty()

    override fun blockCommitted(blockData: BlockData) {
        extensions.filterIsInstance<GTXBlockBuildingAffectingSpecialTxExtension>().forEach { it.blockCommitted(blockData) }
    }

    override fun shouldBuildBlock(): Boolean =
            extensions.filterIsInstance<GTXBlockBuildingAffectingSpecialTxExtension>().any { it.shouldBuildBlock() }

}