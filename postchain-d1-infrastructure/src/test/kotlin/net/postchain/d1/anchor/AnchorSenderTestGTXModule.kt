package net.postchain.d1.anchor

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule

class AnchorSenderTestGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(AnchorTestBBExtension())
    }

    override fun initializeDB(ctx: EContext) {
    }
}