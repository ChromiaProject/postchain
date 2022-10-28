package net.postchain.managed.config

import net.postchain.managed.ManagedNodeDataSource

interface ManagedDataSourceAware {
    val dataSource: ManagedNodeDataSource
}
