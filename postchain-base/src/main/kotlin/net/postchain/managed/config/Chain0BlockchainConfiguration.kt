package net.postchain.managed.config

import net.postchain.config.app.AppConfig
import net.postchain.core.Storage
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.query.GtxModuleQueryRunner

open class Chain0BlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        val appConfig: AppConfig,
        storage: Storage
) : ManagedBlockchainConfiguration(
        configuration,
        BaseManagedNodeDataSource(GtxModuleQueryRunner(configuration, appConfig, storage) { op ->
            val existingWriteCtx = storage.getExistingWriteContext(configuration.chainID)
            val ctx = existingWriteCtx ?: storage.openReadConnection(configuration.chainID)
            try {
                op(ctx)
            } finally {
                if (ctx != existingWriteCtx) storage.closeReadConnection(ctx)
            }
        }, appConfig)
)
