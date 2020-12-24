package net.postchain.ethereum

import net.postchain.core.BlockEContext

open class L2BlockEContext(
    private val ectx: BlockEContext, private val bb: L2BlockBuilder
) : BlockEContext by ectx {
    override fun <T> getInterface(c: Class<T>): T? {
        if (c == L2BlockEContext::class.java) {
            return this as T?
        } else
            return super.getInterface(c)
    }

    fun emitL2Event(evt: ByteArray) {
        bb.appendL2Event(evt)
    }
}