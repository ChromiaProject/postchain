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
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.devtools.NameHelper.peerName
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.metrics.initMetrics

/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(val appConfig: AppConfig, wipeDb: Boolean = false) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    val postchainContext: PostchainContext
    private val logPrefix: String

    companion object : KLogging()

    init {
        initMetrics(appConfig)

        val blockBuilderStorage = StorageBuilder.buildStorage(appConfig, appConfig.databaseBlockBuilderMaxWaitWrite, appConfig.databaseBlockBuilderWriteConcurrency, wipeDb)
        val sharedStorage = StorageBuilder.buildStorage(appConfig, appConfig.databaseSharedMaxWaitWrite, appConfig.databaseSharedWriteConcurrency, wipeDb)

        sharedStorage.withReadConnection { ctx ->
            DatabaseAccess.of(ctx).checkCollation(ctx.conn, suppressError = appConfig.databaseSuppressCollationCheck)
        }

        val infrastructureFactory = BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig)
        logPrefix = peerName(appConfig.pubKey)

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
                blockchainConfigProvider,
                appConfig.debug
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)

        processManager = infrastructureFactory.makeProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigProvider)
        blockQueriesProvider.processManager = processManager
    }

    fun tryStartBlockchain(chainId: Long) {
        withLoggingContext(
                NODE_PUBKEY_TAG to appConfig.pubKey,
                CHAIN_IID_TAG to chainId.toString()) {
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

    fun startBlockchain(chainId: Long): BlockchainRid {
        if (!chainExists(chainId)) {
            throw NotFound("Cannot start chain $chainId, not found in db.")
        }
        return processManager.startBlockchain(chainId, buildBbDebug(chainId))
    }

    private fun chainExists(chainId: Long): Boolean {
        return withReadConnection(postchainContext.sharedStorage, chainId) {
            DatabaseAccess.of(it).getChainIds(it).containsValue(chainId)
        }
    }

    fun stopBlockchain(chainId: Long) {
        processManager.stopBlockchain(chainId, buildBbDebug(chainId))
    }

    fun isBlockchainRunning(chainId: Long): Boolean {
        return processManager.retrieveBlockchain(chainId) != null
    }

    override fun shutdown() {
        withLoggingContext(NODE_PUBKEY_TAG to appConfig.pubKey) {
            // FYI: Order is important
            logger.info("$logPrefix: shutdown() - begin")
            processManager.shutdown()
            logger.debug("$logPrefix: shutdown() - Stopping BlockchainInfrastructure")
            blockchainInfrastructure.shutdown()
            logger.debug("$logPrefix: shutdown() - Stopping PostchainContext")
            postchainContext.shutDown()
            logger.info("$logPrefix: shutdown() - end")
        }
    }

    /**
     * This is for DEBUG operation only
     *
     * @return "true" if we are actually running a test. If we are inside a test we can ofter do more
     * debugging than otherwise
     */
    open fun isThisATest(): Boolean = false

    /**
     * This is for DEBUG operation only
     *
     * We don't care about what the most recent block was, or height at this point.
     * We are just providing the info we have right now
     */
    private fun buildBbDebug(chainId: Long): BlockTrace? {
        return if (logger.isDebugEnabled) {
            withLoggingContext(
                    NODE_PUBKEY_TAG to appConfig.pubKey,
                    CHAIN_IID_TAG to chainId.toString()
            ) {
                val x = processManager.retrieveBlockchain(chainId)
                if (x == null) {
                    logger.trace { "WARN why didn't we find the blockchain for chainId: $chainId on node: ${postchainContext.appConfig.pubKey}?" }
                    null
                } else {
                    val procName = BlockchainProcessName(
                            postchainContext.appConfig.pubKey,
                            x.blockchainEngine.getConfiguration().blockchainRid
                    )
                    BlockTrace.buildBeforeBlock(procName)
                }
            }
        } else {
            null
        }
    }

    private val version get() = javaClass.getPackage()?.implementationVersion ?: "null"
}
