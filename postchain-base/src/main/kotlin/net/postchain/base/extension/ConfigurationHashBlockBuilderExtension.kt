package net.postchain.base.extension

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BaseBlockHeader
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockHeader
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val CONFIG_HASH_EXTRA_HEADER = "config_hash"

class ConfigurationHashBlockBuilderExtension(private val configHash: ByteArray) : BaseBlockBuilderExtension {

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {}

    override fun finalize(): Map<String, Gtv> = mapOf(
            CONFIG_HASH_EXTRA_HEADER to gtv(configHash)
    )
}

fun BlockHeader.getConfigHash(): ByteArray? {
    return (this as? BaseBlockHeader)?.extraData?.get(CONFIG_HASH_EXTRA_HEADER)?.asByteArray()
}
