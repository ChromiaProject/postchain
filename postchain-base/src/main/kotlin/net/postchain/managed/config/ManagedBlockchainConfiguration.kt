package net.postchain.managed.config

import net.postchain.PostchainContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.PostchainContextAware
import net.postchain.managed.ManagedNodeDataSource

open class ManagedBlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        override val dataSource: ManagedNodeDataSource
) : BlockchainConfiguration by configuration, ManagedDataSourceAware, GTXModuleAware {
    override val module = configuration.module

    override fun initializeModules(postchainContext: PostchainContext) {
        val gtxModule = module
        if (gtxModule is PostchainContextAware) {
            gtxModule.initializeContext(this, postchainContext)
        }
    }
}
