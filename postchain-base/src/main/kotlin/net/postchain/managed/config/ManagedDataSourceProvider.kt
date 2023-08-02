package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.Storage
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource

fun interface ManagedDataSourceProvider {
    fun getDataSource(configuration: GTXBlockchainConfiguration, appConfig: AppConfig, storage: Storage): ManagedNodeDataSource
}