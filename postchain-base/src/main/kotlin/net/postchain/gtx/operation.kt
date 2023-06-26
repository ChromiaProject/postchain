// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.exception.UserMistake
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.gtx.data.ExtOpData

class GTXOpMistake(message: String, opData: ExtOpData, argPos: Int? = null, cause: Exception? = null)
    : UserMistake(message +
        " (in ${opData.opName} #${opData.opIndex} " +
        (if (argPos != null) "(arg ${argPos})" else "") + ")",
        cause)

abstract class GTXOperation(val data: ExtOpData) : Transactor {
    override fun isSpecial(): Boolean {
        return data.opName.startsWith("__")  // We used to return "false" here always, but that wos just too confusing IMO
    }
}

class SimpleGTXOperation(data: ExtOpData,
                         val applyF: (TxEContext) -> Boolean,
                         val isCorrectF: () -> Boolean)
    : GTXOperation(data) {
    override fun apply(ctx: TxEContext): Boolean {
        return applyF(ctx)
    }

    override fun checkCorrectness() {
        if (!isCorrectF()) throw UserMistake("Incorrect operation")
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { _, data ->
        SimpleGTXOperation(data, applyF, { true })
    }
}

fun gtxOP(applyF: (TxEContext) -> Boolean, isCorrectF: () -> Boolean): (Unit, ExtOpData) -> Transactor {
    return { _, data ->
        SimpleGTXOperation(data, applyF, isCorrectF)
    }
}
