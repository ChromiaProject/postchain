package net.postchain.base.extension

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val CONFIG_HASH_EXTRA_HEADER = "config_hash"

class ConfigurationHashBlockBuilderExtension(private val configHash: ByteArray) : BaseBlockBuilderExtension {

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {}

    override fun finalize(): Map<String, Gtv> {
        return mapOf(CONFIG_HASH_EXTRA_HEADER to gtv(configHash))
    }
}