// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ConfigurationChangeCheckResult
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext

open class ManagedBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    lateinit var dataSource: ManagedNodeDataSource

    // Used to access Chain0 configs which are preloaded and validated in ManagedBlockchainProcessManager.preloadChain0Configuration().
    private val localProvider = ManualBlockchainConfigurationProvider()

    companion object : KLogging()

    fun setManagedDataSource(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource
    }

    override fun getActiveBlockConfiguration(eContext: EContext, chainId: Long, pendingConfigHashToLoad: ByteArray?): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getActiveBlockConfiguration(eContext, chainId, pendingConfigHashToLoad)
        } else {
            logger.debug { "getConfiguration() - Fetching configuration from chain0 (for chain: $chainId)" }
            if (::dataSource.isInitialized) {
                getConfigurationFromDataSource(eContext, pendingConfigHashToLoad)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long, checkPendingConfigs: Boolean): ConfigurationChangeCheckResult {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.activeBlockNeedsConfigurationChange(eContext, chainId, checkPendingConfigs)
        } else {
            if (::dataSource.isInitialized) {
                checkNeedConfChangeViaDataSource(eContext, checkPendingConfigs)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getHistoricConfigurationHeight(eContext, chainId, historicBlockHeight)
        } else {
            // We ignore local setting, go to Chain0 directly
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)

            // We don't have any efficient query for this in directory chain
            return dataSource.getHistoricConfigurationHeight(blockchainRid, historicBlockHeight)
        }
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getHistoricConfiguration(eContext, chainId, historicBlockHeight)
        } else {
            logger.debug { "getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)" }
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)
            return dataSource.getConfiguration(blockchainRid.data, historicBlockHeight)
        }
    }

    override fun getActiveBlockConfigurationOptions(eContext: EContext, chainId: Long): BlockchainConfigurationOptions {
        return if (chainId == 0L) {
            BlockchainConfigurationOptions.DEFAULT
        } else {
            if (::dataSource.isInitialized) {
                val dba = DatabaseAccess.of(eContext)
                val blockchainRid = getBlockchainRid(eContext, dba)
                val activeHeight = getActiveBlocksHeight(eContext, dba)
                dataSource.getBlockchainConfigurationOptions(blockchainRid, activeHeight)
                        ?: BlockchainConfigurationOptions.DEFAULT
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    fun getMigratingBlockchainNodeInfo(chainId: Long, blockchainRid: BlockchainRid): MigratingBlockchainNodeInfo? {
        return if (chainId != 0L && ::dataSource.isInitialized) dataSource.getMigratingBlockchainNodeInfo(blockchainRid) else null
    }

    // --------- Private --------

    protected fun checkNeedConfChangeViaDataSource(eContext: EContext, checkPendingConfigs: Boolean): ConfigurationChangeCheckResult {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = getBlockchainRid(eContext, dba)
        val lastSavedBlockHeight = dba.getLastBlockHeight(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val logPrefix = "checkNeedConfChangeViaDataSource(${eContext.chainID})"

        val appliedConfigFound = activeHeight == dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedBlockHeight)
        return if (appliedConfigFound) {
            ConfigurationChangeCheckResult(true)
        } else if (checkPendingConfigs) {
            val failedConfigHash = dataSource.getFaultyBlockchainConfiguration(blockchainRid, activeHeight)?.wrap()
                    ?: dba.getFaultyConfiguration(eContext)?.configHash
            val pendingConfig = dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight).find {
                !dba.configurationHashExists(eContext, it.configHash.data)
            }
            val newPendingConfigDetected = pendingConfig != null && pendingConfig.configHash != failedConfigHash
            if (newPendingConfigDetected) {
                logger.debug { "$logPrefix: new pending configuration detected for activeHeight: $activeHeight" }
                ConfigurationChangeCheckResult(true, pendingConfig.configHash.data)
            } else {
                logger.debug { "$logPrefix: no new pending configuration detected for activeHeight: $activeHeight" }
                ConfigurationChangeCheckResult(false)
            }
        } else {
            ConfigurationChangeCheckResult(false)
        }
    }

    fun isConfigPending(eContext: EContext, blockchainRid: BlockchainRid, activeHeight: Long, configHash: ByteArray): Boolean {
        val logPrefix = "isConfigPending(${eContext.chainID}, ${blockchainRid.toShortHex()}, $activeHeight, ${configHash.wrap()})"
        val pendingConfig = getConfigIfPending(eContext, blockchainRid, activeHeight, configHash)
        return if (pendingConfig != null) {
            logger.debug { "$logPrefix: config ${pendingConfig.configHash} will be loaded. Signers: ${pendingConfig.signers}" }
            true
        } else {
            logger.debug { "$logPrefix: all pending configs already applied, config will be loaded from DataSource" }
            false
        }
    }

    fun getConfigIfPending(eContext: EContext, blockchainRid: BlockchainRid, activeHeight: Long, configHash: ByteArray): PendingBlockchainConfiguration? {
        val configs = dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight)
        return if (configs.isNotEmpty()) {
            logger.debug { "${configs.size} pending config(s) detected at height: $activeHeight" }
            val configToApply = configs.firstOrNull {
                !DatabaseAccess.of(eContext).configurationHashExists(eContext, it.configHash.data)
            }
            if (configToApply != null) {
                if (configToApply.configHash == configHash.wrap()) {
                    return configToApply
                } else null
            } else {
                null
            }
        } else { // This branch is chosen after blockchain restarts and if there is no a pending config at activeHeight
            null
        }
    }

    /**
     * Loading of next pending config is determined by [pendingConfigHashToLoad]:
     * if a pending config with that hash exists -> Load it
     * if a pending config with that hash does not exist or hash is null ->
     *      Load the latest applied pending config, if none are applied load from datasource.
     *      If there are no pending configs, load from datasource.
     *
     * Note: Applied means that the config has been used to build at least one block. I.e. it's applied in the context of the blockchain rather than the node.
     * Next pending config is the earliest pending config that has not yet been applied.
     */
    protected fun getConfigurationFromDataSource(eContext: EContext, pendingConfigHashToLoad: ByteArray?): ByteArray? {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = getBlockchainRid(eContext, dba)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val logPrefix = "getConfigurationFromDataSource(${eContext.chainID})"

        return if (activeHeight == 0L) {
            logger.debug { "$logPrefix: the initial config will be loaded from DataSource" }
            dataSource.getConfiguration(blockchainRid.data, 0L)
        } else {
            val lastSavedBlockHeight = dba.getLastBlockHeight(eContext)
            val appliedConfigFound = activeHeight == dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedBlockHeight)
            if (appliedConfigFound) {
                logger.debug { "$logPrefix: appliedConfigFound found at activeHeight, and will be loaded" }
                dataSource.getConfiguration(blockchainRid.data, activeHeight)
            } else {
                val pendingConfigs = dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight)
                if (pendingConfigs.isNotEmpty()) {
                    logger.debug { "$logPrefix: ${pendingConfigs.size} pending config(s) detected at height: $activeHeight" }
                    val wrappedPendingConfigHashToLoad = pendingConfigHashToLoad?.wrap()
                    val configToApply = wrappedPendingConfigHashToLoad?.let {
                                pendingConfigs.find { it.configHash == wrappedPendingConfigHashToLoad }
                            } ?: pendingConfigs.lastOrNull {
                                dba.configurationHashExists(eContext, it.configHash.data)
                            }

                    if (wrappedPendingConfigHashToLoad != null && configToApply?.configHash != wrappedPendingConfigHashToLoad) {
                        logger.info("Requested pending config to load $wrappedPendingConfigHashToLoad was not found, falling back to latest applied config")
                    }

                    if (configToApply != null) {
                        logger.debug { "$logPrefix: config ${configToApply.configHash} will be loaded. Signers: ${configToApply.signers}" }
                        configToApply.fullConfig
                    } else {
                        logger.debug { "$logPrefix: no pending configs are applied, config will be loaded from DataSource" }
                        dataSource.getConfiguration(blockchainRid.data, activeHeight)
                    }
                } else { // This branch is chosen after blockchain restarts and if there is no pending config at activeHeight
                    logger.debug { "$logPrefix: pending config is absent at height: $activeHeight, config will be loaded from DataSource" }
                    dataSource.getConfiguration(blockchainRid.data, activeHeight)
                }
            }
        }
    }

    protected open fun getBlockchainRid(eContext: EContext, dba: DatabaseAccess): BlockchainRid {
        val blockchainRid = dba.getBlockchainRid(eContext)
        checkNotNull(blockchainRid) { "Unknown chainId: ${eContext.chainID}" }
        return blockchainRid
    }
}