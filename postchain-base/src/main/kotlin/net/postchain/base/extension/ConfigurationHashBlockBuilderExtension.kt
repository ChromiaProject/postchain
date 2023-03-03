package net.postchain.base.extension

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val BASE_CONFIG_HASH_EXTRA_HEADER = "base_config_hash"
const val FULL_CONFIG_HASH_EXTRA_HEADER = "full_config_hash"

class ConfigurationHashBlockBuilderExtension(
        private val baseConfigHash: ByteArray,
        private val fullConfigHash: ByteArray) : BaseBlockBuilderExtension {

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {}

    override fun finalize(): Map<String, Gtv> = mapOf(
            BASE_CONFIG_HASH_EXTRA_HEADER to gtv(baseConfigHash),
            FULL_CONFIG_HASH_EXTRA_HEADER to gtv(fullConfigHash)
    )
}
