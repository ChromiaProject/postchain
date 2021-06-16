// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.HistoricBlockchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext

interface SynchronizationInfrastructure : Shutdownable {

    fun init()

    fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            historicBlockchainContext: HistoricBlockchainContext? = null
    ): BlockchainProcess

}

interface BlockchainInfrastructure : SynchronizationInfrastructure {

    fun makeBlockchainConfiguration(rawConfigurationData: ByteArray,
                                    eContext: EContext,
                                    nodeId: Int,
                                    chainId: Long
    ): BlockchainConfiguration

    fun makeBlockchainEngine(
            processName: BlockchainProcessName,
            configuration: BlockchainConfiguration,
            restartHandler: RestartHandler
    ): BlockchainEngine

}

interface ApiInfrastructure : Shutdownable {
    fun connectProcess(process: BlockchainProcess)
    fun disconnectProcess(process: BlockchainProcess)
}

interface InfrastructureFactory {

    fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider

    fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure

    fun makeProcessManager(nodeConfigProvider: NodeConfigurationProvider,
                           blockchainInfrastructure: BlockchainInfrastructure,
                           blockchainConfigurationProvider: BlockchainConfigurationProvider,
                           nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager
}

enum class Infrastructure(vararg val key: String) {
    Ebft("ebft", "base-ebft", "base/ebft"),
    EbftManaged("ebft-managed", "net.postchain.managed.ManagedEBFTInfrastructureFactory"), // compatibility
    EbftManagedChromia0("chromia0", "ebft-managed-chromia0", "net.postchain.managed.Chromia0InfrastructureFactory"), // compatibility

    // Container chains
    EbftManagedContainerMaster("ebft-managed-container-master"),
    EbftContainerSlave("ebft-container-slave"),
    EbftManagedChromia0ContainerMaster("ebft-managed-chromia0-container-master"),

    // Tests
    BaseTest("base-test", "base/test");

    fun get(): String = key.first()
}

interface InfrastructureFactoryProvider {
    fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory
}