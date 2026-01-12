// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.special

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.FaultyExtensionException
import net.postchain.core.ExtensionBroadcaster
import net.postchain.core.Transaction
import net.postchain.core.block.BlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.BroadcastAware
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
 * - Can find out if we need a special tx, and
 * - can create a special tx, and
 * - can validate a special tx.
 *
 * Special transactions are usually created by a [GTXSpecialTxExtension], which makes this extendable.
 */
open class GTXSpecialTxHandler(
        val module: GTXModule,
        val chainID: Long,
        val blockchainRID: BlockchainRid,
        val cs: CryptoSystem,
        val factory: GTXTransactionFactory,
        val extensionBroadcaster: ExtensionBroadcaster,
) : SpecialTransactionHandler {

    private val extensions: List<GTXSpecialTxExtension> = module.getSpecialTxExtensions()
    private val opToExtension: Map<String, GTXSpecialTxExtension> = buildMap {
        for (ext in extensions) {
            ext.init(module, chainID, blockchainRID, cs)
            if (ext is BroadcastAware) {
                ext.initializeBroadcastContext { data ->
                    extensionBroadcaster.broadcast(ext.javaClass.name, data)
                }
            }
            for (op in ext.getRelevantOps()) {
                if (containsKey(op)) {
                    throw ProgrammerMistake("Overlapping op: $op")
                }
                put(op, ext)
            }
        }
    }

    companion object : KLogging() {
        const val VALIDATE_SPECIAL_TRANSACTION = "validateSpecialTransaction() -- {}, position: {}"
    }

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return extensions.any { it.needsSpecialTransaction(position) }
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction? {
        val ops = mutableListOf<GtxOp>()
        for (ext in extensions) {
            if (needsSpecialTransaction(ext, position)) {
                try {
                    for (o in ext.createSpecialOperations(position, bctx)) {
                        ops.add(GtxOp(o.opName, *o.args))
                    }
                } catch (e: Exception) {
                    throw FaultyExtensionException("Unexpected exception when creating special transaction at position: $position", e)
                }
            }
        }
        // if no extension emitted an operation, we don't create any tx
        if (ops.isEmpty()) return null

        val hasSpecNop = ops.any { it.opName == GtxSpecNop.OP_NAME }
        if (!hasSpecNop) {
            ops.addLast(GtxOp(GtxSpecNop.OP_NAME, gtv(bctx.height)))
        }
        val tx = Gtx(GtxBody(blockchainRID, ops, listOf()), listOf())
        return factory.decodeTransaction(tx.encode())
    }

    /**
     * The goal of this method is to call "validateSpecialOperations()" on all extensions we have.
     *
     * NOTE: For the logic below to work, no two extensions can have operations with the same name. If they do, we
     *       might use the wrong extension to validate an operation.
     *
     * @param position is the position we are investigating
     * @param tx is the [Transaction] we are investigating (must already have been created at an earlier stage).
     *           This tx holds all operations from all extensions, so it can be very big (in the case of an Anchoring chain at least)
     * @param bctx
     * @return true
     * @throws UserMistake if any special operation is invalid
     */
    override fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, bctx: BlockEContext): Boolean {
        withLoggingContext(TRANSACTION_RID_TAG to tx.getRID().toHex()) {
            logger.trace(VALIDATE_SPECIAL_TRANSACTION, "Begin", position)

            val operations = (tx as GTXTransaction).gtxData.gtxBody.operations.map { it.asOpData() }

            // empty ops
            if (operations.isEmpty()) {
                throw UserMistake("Empty operation list is not allowed")
            }

            // __nop
            val nopIdx = operations.indexOfFirst { it.opName == GtxSpecNop.OP_NAME }
            if (nopIdx != -1 && nopIdx != operations.lastIndex) {
                throw UserMistake("${GtxSpecNop.OP_NAME} is allowed only as the last operation")
            }

            val extOps = operations
                    .filter { it.opName != GtxSpecNop.OP_NAME }
                    .groupBy { opToExtension[it.opName] }

            // unknown ops
            if (extOps.containsKey(null)) {
                throw UserMistake("Unknown operation detected: ${extOps[null]?.toTypedArray()?.contentToString()}")
            }

            // ext validation
            extOps.forEach { (ext, ops) ->
                if (ext != null) {
                    if (!needsSpecialTransaction(ext, position)) {
                        throw UserMistake("Special handler ${ext.javaClass.name} does not need special transaction at position: $position")
                    }

                    val isValid = try {
                        ext.validateSpecialOperations(position, bctx, ops)
                    } catch (e: UserMistake) {
                        throw UserMistake("Validation failed in special handler ${ext.javaClass.name}: ${e.message}")
                    } catch (e: Exception) {
                        // Extensions should not throw anything else than `UserMistake` when validating
                        throw FaultyExtensionException("Unexpected exception while validating transaction at position: $position", e)
                    }
                    if (!isValid) {
                        throw UserMistake("Validation failed in special handler ${ext.javaClass.name}")
                    }
                }
            }
            extensions.filterIsInstance<GTXNonSkippingSpecialTxExtension>()
                    .filterNot { extOps.keys.contains(it) }
                    .forEach { skippedExtension ->
                        if (!skippedExtension.isAllowedToSkipSpecialOperations(position, bctx)) {
                            throw UserMistake("Skipping special operations is not allowed by handler ${skippedExtension.javaClass.name}")
                        }
                    }
            logger.trace(VALIDATE_SPECIAL_TRANSACTION, "End", position)
        }
        return true
    }

    private fun needsSpecialTransaction(ext: GTXSpecialTxExtension, position: SpecialTransactionPosition): Boolean = try {
        ext.needsSpecialTransaction(position)
    } catch (e: Exception) {
        throw FaultyExtensionException("Unexpected exception while checking needsSpecialTransaction at position: $position", e)
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

    override fun receiveBroadcast(extensionClass: String, data: Gtv) {
        val extension = extensions.find { it.javaClass.name == extensionClass }
        if (extension != null) {
            if (extension is BroadcastAware) {
                logger.debug { "Receiving extension broadcast for extension $extensionClass" }
                extension.receiveBroadcast(data)
            } else {
                logger.warn("Got extension broadcast for non-broadcast aware extension $extensionClass")
            }
        } else {
            logger.warn("Got extension broadcast for unknown extension $extensionClass")
        }
    }

}