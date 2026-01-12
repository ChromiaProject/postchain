package net.postchain.base

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockEContext
import net.postchain.core.Transaction
import net.postchain.gtv.Gtv

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

    override fun isAllowedToSkipSpecialTransaction(position: SpecialTransactionPosition, bctx: BlockEContext): Boolean {
        throw ProgrammerMistake("NullSpecialTransactionHandler.isAllowedToSkipSpecialTransaction")
    }

    override fun receiveBroadcast(extensionClass: String, data: Gtv) {
        throw ProgrammerMistake("NullSpecialTransactionHandler.receiveBroadcast")
    }
}