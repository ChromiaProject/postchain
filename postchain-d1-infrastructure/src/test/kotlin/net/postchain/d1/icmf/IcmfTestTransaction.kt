package net.postchain.d1.icmf

import net.postchain.core.TxEContext
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory.gtv

class IcmfTestTransaction(id: Int, good: Boolean = true, correct: Boolean = true) : TestTransaction(id, good, correct) {

    override fun apply(ctx: TxEContext): Boolean {
        ctx.emitEvent(ICMF_EVENT_TYPE, gtv("test"))
        return true
    }
}