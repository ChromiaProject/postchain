package net.postchain.base.extension

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val FAILED_CONFIG_HASH_EXTRA_HEADER = "failed_config_hash"

class FailedConfigurationHashBlockBuilderExtension(private val failedConfigHash: ByteArray) : BaseBlockBuilderExtension {

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {}

    override fun finalize(): Map<String, Gtv> = mapOf(
            FAILED_CONFIG_HASH_EXTRA_HEADER to gtv(failedConfigHash)
    )
}