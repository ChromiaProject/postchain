package net.postchain.managed.config

import net.postchain.managed.ManagedNodeDataSource

interface Chain0BlockchainConfigurationInterface {
    var dataSource: ManagedNodeDataSource
}