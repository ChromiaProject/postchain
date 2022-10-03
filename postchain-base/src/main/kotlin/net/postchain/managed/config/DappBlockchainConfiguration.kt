package net.postchain.managed.config

import net.postchain.core.BlockchainConfiguration
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource

open class DappBlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        override val dataSource: ManagedNodeDataSource
) : BlockchainConfiguration by configuration, ManagedDataSourceAwareness