package net.postchain.base

import net.postchain.core.BlockEContext
import net.postchain.core.Transaction

enum class SpecialTransactionPosition {
    Begin, End
}

interface SpecialTransactionHandler {
    fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean
    fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction
    fun validateSpecialTransaction(position: SpecialTransactionPosition, tx: Transaction, bctx: BlockEContext): Boolean
    fun isAllowedToSkipSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Boolean
}