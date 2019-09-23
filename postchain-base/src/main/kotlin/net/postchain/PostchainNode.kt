// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain

import mu.KLogging
import net.postchain.base.BaseTestInfrastructureFactory
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.core.Infrastructures.BaseEbft
import net.postchain.core.Infrastructures.BaseTest
import net.postchain.core.Shutdownable
import net.postchain.ebft.BaseEBFTInfrastructureFactory

/**
 * Postchain node instantiates infrastructure and blockchain
 * process manager.
 */
open class PostchainNode(nodeConfigProvider: NodeConfigurationProvider) : Shutdownable {
    companion object: KLogging()

    val processManager: BlockchainProcessManager
    protected val blockchainInfrastructure: BlockchainInfrastructure

    init {
        val infrastructureFactory = buildInfrastructureFactory(nodeConfigProvider)

        blockchainInfrastructure = infrastructureFactory.makeBlockchainInfrastructure(nodeConfigProvider)
        processManager = infrastructureFactory.makeProcessManager(
                nodeConfigProvider, blockchainInfrastructure
        )
    }

    fun startBlockchain(chainID: Long) {
        processManager.startBlockchain(chainID)
    }

    fun stopBlockchain(chainID: Long) {
        processManager.stopBlockchain(chainID)
    }

    override fun shutdown() {
        processManager.shutdown()
    }

    private fun buildInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory {
        val factoryClass = when (val infrastructureIdentifier = nodeConfigProvider.getConfiguration().infrastructure) {
            BaseEbft.secondName.toLowerCase() -> BaseEBFTInfrastructureFactory::class.java
            BaseTest.secondName.toLowerCase() -> BaseTestInfrastructureFactory::class.java
            else -> {
                try {
                    Class.forName(infrastructureIdentifier)
                } catch (e: ClassNotFoundException) {
                    logger.warn("Could not find infrastructure <$infrastructureIdentifier>, falling back to base/ebft")
                    BaseEBFTInfrastructureFactory::class.java
                }
            }
        }
        return factoryClass.newInstance() as InfrastructureFactory
    }
}