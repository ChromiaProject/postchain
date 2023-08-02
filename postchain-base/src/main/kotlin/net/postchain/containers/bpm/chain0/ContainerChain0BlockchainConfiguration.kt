package net.postchain.containers.bpm.chain0

import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedBlockchainConfiguration

class ContainerChain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        dataSource: ManagedNodeDataSource
) : ManagedBlockchainConfiguration(
        configuration, dataSource
)
