package net.postchain.managed.config

import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource

open class Chain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        dataSource: ManagedNodeDataSource
) : ManagedBlockchainConfiguration(
        configuration, dataSource
)
