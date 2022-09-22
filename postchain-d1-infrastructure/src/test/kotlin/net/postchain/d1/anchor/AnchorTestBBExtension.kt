package net.postchain.d1.anchor

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.d1.anchor.integration.AnchorIntegrationTest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

class AnchorTestBBExtension : BaseBlockBuilderExtension {
    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {

    }

    override fun finalize(): Map<String, Gtv> {
        return mapOf(
                "icmf_send" to gtv(
                        mapOf(
                                "my-topic" to gtv(
                                        mapOf(
                                                "hash" to gtv(AnchorIntegrationTest.messagesHash),
                                                "prev_message_block_height" to gtv(-1)
                                        )
                                )
                        )
                )
        )
    }
}
