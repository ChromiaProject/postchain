package net.postchain.base

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockEContext
import net.postchain.core.Transaction

class NullSpecialTransactionHandler : SpecialTransactionHandler {
    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
        return false
    }

    override fun createSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Transaction {
        throw ProgrammerMistake("NullSpecialTransactionHandler.createSpecialTransaction")
    }

    override fun validateSpecialTransaction(
            position: SpecialTransactionPosition,
            tx: Transaction,
            bctx: BlockEContext
    ): Boolean {
        throw ProgrammerMistake("NullSpecialTransactionHandler.createSpecialTransaction")
    }
}