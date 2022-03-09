// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain

import mu.KLogging
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BaseInfrastructureFactoryProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DefaultNodeDiagnosticContext
import net.postchain.debug.DiagnosticProperty
import net.postchain.devtools.NameHelper.peerName
import nl.komponents.kovenant.Kovenant

/**
 * Postchain node instantiates infrastructure and blockchain process manager.
 */
open class PostchainNode(val nodeConfigProvider: NodeConfigurationProvider) : Shutdownable {

    protected val blockchainInfrastructure: BlockchainInfrastructure
    val processManager: BlockchainProcessManager
    private val diagnosticContext = DefaultNodeDiagnosticContext()
    private val postchainContext: PostchainContext

    companion object : KLogging()

    init {
        Kovenant.context {
            workerContext.dispatcher {
                name = "main"
                concurrentTasks = 5
            }
        }

        val infrastructureFactory = BaseInfrastructureFactoryProvider().createInfrastructureFactory(nodeConfigProvider)
        val connectionManager = infrastructureFactory.makeConnectionManager(nodeConfigProvider)
        postchainContext = PostchainContext(
                nodeConfigProvider.getConfiguration(),
                connectionManager,
                DefaultNodeDiagnosticContext()
        )
        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(postchainContext)
        val blockchainConfigProvider = infrastructureFactory.makeBlockchainConfigurationProvider()
        processManager = infrastructureFactory.makeProcessManager(
                nodeConfigProvider, blockchainInfrastructure, blockchainConfigProvider, diagnosticContext, connectionManager)

        diagnosticContext.addProperty(DiagnosticProperty.VERSION, getVersion())
        diagnosticContext.addProperty(DiagnosticProperty.PUB_KEY, nodeConfigProvider.getConfiguration().pubKey)
        diagnosticContext.addProperty(DiagnosticProperty.BLOCKCHAIN_INFRASTRUCTURE, blockchainInfrastructure.javaClass.simpleName)
    }

    fun startBlockchain(chainId: Long): BlockchainRid? {
        return processManager.startBlockchain(chainId, buildBbDebug(chainId))
    }

    fun stopBlockchain(chainId: Long) {
        processManager.stopBlockchain(chainId, buildBbDebug(chainId))
    }

    override fun shutdown() {
        // FYI: Order is important
        logger.info("${name()}: shutdown() - begin")
        processManager.shutdown()
        logger.debug("${name()}: shutdown() - Stopping BlockchainInfrastructure")
        blockchainInfrastructure.shutdown()
        logger.debug("${name()}: shutdown() - Stopping ConnectionManager")
        postchainContext.shutDown()
        logger.debug("${name()}: shutdown() - Closing NodeConfigurationProvider")
        nodeConfigProvider.close()
        logger.info("${name()}: shutdown() - end")
    }

    private fun name(): String {
        return peerName(diagnosticContext.getProperty(DiagnosticProperty.PUB_KEY).toString())
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
                logger.trace{ "WARN why didn't we find the blockchain for chainId: $chainId on node: ${nodeConfigProvider.getConfiguration().pubKey}?" }
                null
            } else {
                val procName = BlockchainProcessName(nodeConfigProvider.getConfiguration().pubKey, x.blockchainEngine.getConfiguration().blockchainRid)
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
