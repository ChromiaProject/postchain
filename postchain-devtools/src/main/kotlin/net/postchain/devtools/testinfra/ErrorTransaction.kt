// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.core.TxEContext

class ErrorTransaction(id: Int, private val applyThrows: Boolean, private val checkCorrectnessThrows: Boolean) : TestTransaction(id) {
    override fun checkCorrectness() {
        if (checkCorrectnessThrows) throw TransactionIncorrect(getRID(), "Thrown from checkCorrectness()")
    }

    override fun isSpecial(): Boolean {
        return false
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (applyThrows) throw UserMistake("Thrown from apply()")
        return true
    }
}