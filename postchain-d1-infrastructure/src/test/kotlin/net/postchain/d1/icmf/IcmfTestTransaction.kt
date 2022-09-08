package net.postchain.d1.icmf

import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory.gtv

class IcmfTestTransaction(id: Int, val op: Transactor, good: Boolean = true, correct: Boolean = true) : TestTransaction(id, good, correct) {

    override fun apply(ctx: TxEContext): Boolean {
        op.isCorrect()
        op.apply(ctx)
        // ctx.emitEvent(ICMF_EVENT_TYPE, IcmfMessage("topic", gtv("test$id")).toGtv())
        return true
    }
}