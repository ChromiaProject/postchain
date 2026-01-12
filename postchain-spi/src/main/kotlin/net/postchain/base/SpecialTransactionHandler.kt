package net.postchain.base

import net.postchain.core.BlockEContext
import net.postchain.core.Transaction
import net.postchain.core.block.BlockData
import net.postchain.gtv.Gtv

enum class SpecialTransactionPosition {
    Begin, End
}

interface SpecialTransactionHandler {
    fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean
    fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction?
    fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, bctx: BlockEContext): Boolean
    fun isAllowedToSkipSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Boolean

    /**
     * Will be called from [net.postchain.base.BaseBlockBuilderExtension].
     *
     * @return true if block building should be affected
     */
    fun shouldAffectBlockBuilding(): Boolean = false

    /**
     * Will be called from [net.postchain.base.BaseBlockBuilderExtension] when a block has been built.
     *
     * @param blockData  the block
     */
    fun blockCommitted(blockData: BlockData) {}

    /**
     * Will be called from [net.postchain.base.BaseBlockBuilderExtension].
     *
     * @return true if it is appropriate to build a block now
     */
    fun shouldBuildBlock(): Boolean = false

    fun receiveBroadcast(extensionClass: String, data: Gtv)
}
