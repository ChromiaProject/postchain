// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.StorageBuilder
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders.File
import net.postchain.config.node.NodeConfigProviders.Legacy
import net.postchain.config.node.NodeConfigProviders.Managed
import net.postchain.config.node.NodeConfigProviders.Manual
import net.postchain.core.NODE_ID_NA
import net.postchain.core.Storage

object NodeConfigurationProviderFactory {
    private val DEFAULT_STORAGE_FACTORY: (AppConfig) -> Storage = {
        StorageBuilder.buildStorage(it, NODE_ID_NA)
    }

    /**
     * @param appConfig used to find the provider
     * @param storageFactory
     * @return the correct [NodeConfigurationProvider] based on [AppConfig]'s setting
     */
    fun createProvider(
            appConfig: AppConfig,
            storageFactory: (AppConfig) -> Storage = DEFAULT_STORAGE_FACTORY
    ): NodeConfigurationProvider {
        return when (appConfig.nodeConfigProvider.toLowerCase()) {
            Legacy.name.toLowerCase() -> LegacyNodeConfigurationProvider(appConfig)
            File.name.toLowerCase() -> FileNodeConfigurationProvider(appConfig)
            Manual.name.toLowerCase() -> ManualNodeConfigurationProvider(appConfig, storageFactory)
            Managed.name.toLowerCase() -> ManagedNodeConfigurationProvider(appConfig, storageFactory)
            else -> ManualNodeConfigurationProvider(appConfig, storageFactory)
        }
    }
}