// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.common.exception.UserMistake
import net.postchain.config.app.AppConfig
import net.postchain.core.BaseInfrastructureFactoryProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.Shutdownable
import net.postchain.core.block.BlockQueriesProviderImpl
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.metrics.initMetrics
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(val appConfig: AppConfig, wipeDb: Boolean = false) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    val postchainContext: PostchainContext

    companion object : KLogging()

    init {
        initMetrics(appConfig)

        val blockBuilderStorage = StorageBuilder.buildStorage(
                appConfig,
                appConfig.databaseBlockBuilderMaxWaitWrite.toDuration(DurationUnit.MILLISECONDS),
                appConfig.databaseBlockBuilderWriteConcurrency,
                wipeDb)
        val sharedStorage = StorageBuilder.buildStorage(appConfig,
                appConfig.databaseSharedMaxWaitWrite.toDuration(DurationUnit.MILLISECONDS),
                appConfig.databaseSharedWriteConcurrency,
                wipeDb)

        sharedStorage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).checkCollation(ctx.conn, suppressError = appConfig.databaseSuppressCollationCheck)
        }

        val infrastructureFactory = BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig)

        val blockQueriesProvider = BlockQueriesProviderImpl()
        val blockchainConfigProvider = infrastructureFactory.makeBlockchainConfigurationProvider()
        postchainContext = PostchainContext(
                appConfig,
                infrastructureFactory.makeNodeConfigurationProvider(appConfig, sharedStorage),
                blockBuilderStorage,
                sharedStorage,
                infrastructureFactory.makeConnectionManager(appConfig),
                blockQueriesProvider,
                JsonNodeDiagnosticContext(version, appConfig.pubKey, infrastructureFactory),
                blockchainConfigProvider
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)
        processManager = infrastructureFactory.makeProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigProvider)
        blockQueriesProvider.processManager = processManager
    }

    fun tryStartBlockchain(chainId: Long) {
        withLoggingContext(CHAIN_IID_TAG to chainId.toString()) {
            try {
                startBlockchain(chainId)
            } catch (e: NotFound) {
                logger.error(e.message)
            } catch (e: UserMistake) {
                logger.error(e.message)
            } catch (e: Exception) {
                logger.error(e) { e.message }
            }
        }
    }

    open fun startBlockchain(chainId: Long): BlockchainRid {
        if (!chainExists(chainId)) {
            throw NotFound("Cannot start chain $chainId, not found in db.")
        }
        return processManager.startBlockchain(chainId, null)
    }

    private fun chainExists(chainId: Long): Boolean {
        return withReadConnection(postchainContext.sharedStorage, chainId) {
            DatabaseAccess.of(it).getChainIds(it).containsValue(chainId)
        }
    }

    open fun stopBlockchain(chainId: Long) {
        processManager.stopBlockchain(chainId, null)
    }

    fun isBlockchainRunning(chainId: Long): Boolean {
        return processManager.retrieveBlockchain(chainId)?.isProcessRunning() ?: false
    }

    override fun shutdown() {
        // FYI: Order is important
        logger.info("shutdown() - begin")
        processManager.shutdown()
        logger.debug("shutdown() - Stopping BlockchainInfrastructure")
        blockchainInfrastructure.shutdown()
        logger.debug("shutdown() - Stopping PostchainContext")
        postchainContext.shutDown()
        logger.info("shutdown() - end")
    }

    /**
     * This is for DEBUG operation only
     *
     * @return "true" if we are actually running a test. If we are inside a test we can ofter do more
     * debugging than otherwise
     */
    open fun isThisATest(): Boolean = false

    private val version get() = javaClass.getPackage()?.implementationVersion ?: "null"
}
