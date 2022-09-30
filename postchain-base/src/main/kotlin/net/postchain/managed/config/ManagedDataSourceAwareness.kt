package net.postchain.managed.config

import net.postchain.managed.ManagedNodeDataSource

interface ManagedDataSourceAwareness {
    val dataSource: ManagedNodeDataSource
}