package net.postchain.devtools.testinfra

import net.postchain.core.TxEContext
import java.lang.Thread.sleep

class DelayedTransaction(
        id: Int,
        private val delay: Long
) : TestTransaction(id) {

    override fun apply(ctx: TxEContext): Boolean {
        sleep(delay)
        return super.apply(ctx)
    }
}