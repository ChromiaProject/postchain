// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.core.UserMistake

class GTXOpMistake(message: String, opData: ExtOpData, argPos: Int? = null, cause: Exception? = null)
    : UserMistake(message +
        " (in ${opData.opName} #${opData.opIndex} " +
        (if (argPos != null) "(arg ${argPos})" else "") + ")",
        cause)

abstract class GTXOperation(val data: ExtOpData) : Transactor {
    override fun isSpecial(): Boolean = false
}

class SimpleGTXOperation(data: ExtOpData,
                         val applyF: (TxEContext) -> Boolean,
                         val isCorrectF: () -> Boolean)
    : GTXOperation(data) {
    override fun apply(ctx: TxEContext): Boolean {
        return applyF(ctx)
    }

    override fun isCorrect(): Boolean {
        return isCorrectF()
    }

    override fun isSpecial(): Boolean {
        return data.opName.startsWith("__")
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { unit, data ->
        SimpleGTXOperation(data, applyF, { true })
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean, isCorrectF: () -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { unit, data ->
        SimpleGTXOperation(data, applyF, isCorrectF)
    }
}

/**
 * A Special operation, i.e. not created by a user.
 */
class GtxSpecialOperation(u: Unit, opData: ExtOpData) : GTXOperation(opData) {

    companion object : KLogging() {
    }

    override fun apply(ctx: TxEContext): Boolean {
        return true // TODO: Olle (what to do?)
    }

    override fun isCorrect(): Boolean {
        return true // TODO: Olle (what to do?)
    }

    override fun isSpecial(): Boolean {
        return true // At least this should be obvious
    }

}

