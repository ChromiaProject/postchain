package net.postchain.base

import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv

fun interface TxEventSink {
    fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv)
}