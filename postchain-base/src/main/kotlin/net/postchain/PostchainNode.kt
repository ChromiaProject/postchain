// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.NotFound
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.BaseInfrastructureFactoryProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.Shutdownable
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DefaultNodeDiagnosticContext
import net.postchain.debug.DiagnosticProperty
import net.postchain.devtools.NameHelper.peerName
import net.postchain.metrics.CHAIN_IID_TAG
import net.postchain.metrics.NODE_PUBKEY_TAG
import net.postchain.metrics.initMetrics
import nl.komponents.kovenant.Kovenant


/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(val appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    val postchainContext: PostchainContext
    private val logPrefix: String

    companion object : KLogging()

    init {
        initMetrics(appConfig)

        Kovenant.context {
            workerContext.dispatcher {
                name = "main"
                concurrentTasks = 5
            }
        }
        val storage = StorageBuilder.buildStorage(appConfig, wipeDb)

        val infrastructureFactory = BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig)
        logPrefix = peerName(appConfig.pubKey)

        val chain0QueryProvider = object : () -> Query?, BlockchainProcessManagerHolder {
            override lateinit var myProcessManager: BlockchainProcessManager

            override operator fun invoke(): Query? {
                val blockQueries = myProcessManager.retrieveChain0()?.blockchainEngine?.getBlockQueries()
                return if (blockQueries != null) {
                    { name, args -> blockQueries.query(name, args).get() }
                } else {
                    null
                }
            }
        }
        postchainContext = PostchainContext(
                appConfig,
                NodeConfigurationProviderFactory.createProvider(appConfig) { storage },
                storage,
                infrastructureFactory.makeConnectionManager(appConfig),
                if (debug) DefaultNodeDiagnosticContext() else null,
                chain0QueryProvider
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)
        val blockchainConfigProvider = infrastructureFactory.makeBlockchainConfigurationProvider()
        processManager = infrastructureFactory.makeProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigProvider)
        chain0QueryProvider.myProcessManager = processManager

        postchainContext.nodeDiagnosticContext?.apply {
            addProperty(DiagnosticProperty.VERSION, getVersion())
            addProperty(DiagnosticProperty.PUB_KEY, appConfig.pubKey)
            addProperty(DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE, blockchainInfrastructure.javaClass.simpleName)
        }
    }

    fun startBlockchain(chainId: Long): BlockchainRid {
        if (!chainExists(chainId)) {
            throw NotFound("Cannot start chain $chainId, not found in db.")
        }
        return processManager.startBlockchain(chainId, buildBbDebug(chainId))
    }

    private fun chainExists(chainId: Long): Boolean {
        return withReadConnection(postchainContext.storage, chainId) {
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

    private fun getVersion(): String {
        return javaClass.getPackage()?.implementationVersion ?: "null"
    }

    interface BlockchainProcessManagerHolder {
        var myProcessManager: BlockchainProcessManager
    }
}
