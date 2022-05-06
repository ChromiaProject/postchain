// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import mu.KLogging
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory
import net.postchain.core.*
import net.postchain.common.BlockchainRid
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DefaultNodeDiagnosticContext
import net.postchain.debug.DiagnosticProperty
import net.postchain.devtools.NameHelper.peerName
import nl.komponents.kovenant.Kovenant

/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(appConfig: AppConfig, wipeDb: Boolean = false, debug: Boolean = false) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    protected val postchainContext: PostchainContext
    private val logPrefix: String

    companion object : KLogging()

    init {
        Kovenant.context {
            workerContext.dispatcher {
                name = "main"
                concurrentTasks = 5
            }
        }
        val storage = StorageBuilder.buildStorage(appConfig, wipeDb)

        val infrastructureFactory = BaseInfrastructureFactoryProvider.createInfrastructureFactory(appConfig)
        logPrefix = peerName(appConfig.pubKey)

        postchainContext = PostchainContext(
                appConfig,
                NodeConfigurationProviderFactory.createProvider(appConfig) { storage },
                storage,
                infrastructureFactory.makeConnectionManager(appConfig),
                if (debug) DefaultNodeDiagnosticContext() else null
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)
        val blockchainConfigProvider = infrastructureFactory.makeBlockchainConfigurationProvider()
        processManager = infrastructureFactory.makeProcessManager(postchainContext, blockchainInfrastructure, blockchainConfigProvider)

        postchainContext.nodeDiagnosticContext?.apply {
            addProperty(DiagnosticProperty.VERSION, getVersion())
            addProperty(DiagnosticProperty.PUB_KEY, appConfig.pubKey)
            addProperty(DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE, blockchainInfrastructure.javaClass.simpleName)
        }
    }

    fun startBlockchain(chainId: Long): BlockchainRid? {
        return processManager.startBlockchain(chainId, buildBbDebug(chainId))
    }

    fun stopBlockchain(chainId: Long) {
        processManager.stopBlockchain(chainId, buildBbDebug(chainId))
    }

    override fun shutdown() {
        // FYI: Order is important
        logger.info("$logPrefix: shutdown() - begin")
        processManager.shutdown()
        logger.debug("$logPrefix: shutdown() - Stopping BlockchainInfrastructure")
        blockchainInfrastructure.shutdown()
        logger.debug("$logPrefix: shutdown() - Stopping PostchainContext")
        postchainContext.shutDown()
        logger.info("$logPrefix: shutdown() - end")
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
            val x = processManager.retrieveBlockchain(chainId)
            if (x == null) {
                logger.trace { "WARN why didn't we find the blockchain for chainId: $chainId on node: ${postchainContext.appConfig.pubKey}?" }
                null
            } else {
                val procName = BlockchainProcessName(postchainContext.appConfig.pubKey, x.blockchainEngine.getConfiguration().blockchainRid)
                BlockTrace.buildBeforeBlock(procName)
            }
        } else {
            null
        }
    }

    private fun getVersion(): String {
        return javaClass.getPackage()?.implementationVersion ?: "null"
    }
}
