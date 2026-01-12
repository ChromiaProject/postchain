package net.postchain.managed.config

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcessConnectable
import net.postchain.core.EContext
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.PostchainContextAware
import net.postchain.managed.ManagedNodeDataSource

open class ManagedBlockchainConfiguration(
        val configuration: GTXBlockchainConfiguration,
        override val dataSource: ManagedNodeDataSource
) : BlockchainConfiguration by configuration, ManagedDataSourceAware, GTXModuleAware, BlockchainProcessConnectable by configuration {
    override val module = configuration.module

    override fun initializeModules(postchainContext: PostchainContext, ctx: EContext) {
        val gtxModule = module
        if (gtxModule is PostchainContextAware) {
            gtxModule.initializeContext(this, postchainContext, ctx)
        }
    }
}
