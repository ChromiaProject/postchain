package net.postchain.d1.anchor

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class AnchorTestBBExtension : BaseBlockBuilderExtension {
    override fun init(blockEContext: BlockEContext, baseBB: BlockBuilder) {

    }

    override fun finalize(): Map<String, Gtv> {
        return mapOf("icmf_send" to gtv(mapOf("my-topic" to gtv(ByteArray(32)))))
    }
}