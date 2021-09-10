// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.HistoricBlockchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext

/**
 * Responsible blockchain process lifecycle, i.e. creating, exiting and restarting blockchain processes.
 */
interface SynchronizationInfrastructure : Shutdownable {

    /**
     * This is how a blockchain process get created.
     */
    fun makeBlockchainProcess(
        processName: BlockchainProcessName,
        engine: BlockchainEngine,
        historicBlockchainContext: HistoricBlockchainContext? = null
    ): BlockchainProcess

    /**
     * Call this hook upon blockchain process restart.
     * Note: responsible for keeping track of the two BC process sync modes (normal sync and fastsync)
     */
    fun restartBlockchainProcess(process: BlockchainProcess)

    /**
     * Call this hook before blockchain process is killed.
     * Note: responsible for keeping track of the two BC process sync modes (normal sync and fastsync)
     */
    fun exitBlockchainProcess(process: BlockchainProcess)
}

/**
 * This interface works a bit like a lifecycle hook, basically you can create a chunk of logic that can use
 * a [BlockchainProcess] for something during startup of the process.
 *
 * NOTE: Remember that the Sync Infra Extension is just a part of many extension interfaces working together
 * (examples: BBB Ext and GTX Spec TX Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 *
 */
interface SynchronizationInfrastructureExtension: Shutdownable {

    /**
     * "connect" here is a loosely defined concept. Often we want to initiate the corresponding [GTXSpecialTxExtension]
     * during "connect" but it could be anything.
     *
     * @param process is the new process being created (that has this [SynchronizationInfrastructureExtension] on it).
     */
    fun connectProcess(process: BlockchainProcess)
}

/**
 * Extends the [SynchronizationInfrastructure] with these BC related concepts:
 * 1. [BlockchainConfiguration]
 * 2. [BlockchainEngine]
 */
interface BlockchainInfrastructure : SynchronizationInfrastructure {

    fun makeBlockchainConfiguration(rawConfigurationData: ByteArray,
                                    eContext: EContext,
                                    nodeId: Int,
                                    chainId: Long,
                                    configurationComponentMap: MutableMap<String, Any> = HashMap() // For unusual settings, customizations etc.
    ): BlockchainConfiguration

    fun makeBlockchainEngine(
        processName: BlockchainProcessName,
        configuration: BlockchainConfiguration,
        afterCommitHandler: (BlockTrace?, Long) -> Boolean
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

enum class Infrastructures(val secondName: String) {
    BaseEbft("base/ebft"),
    BaseTest("base/test")
}

interface InfrastructureFactoryProvider {
    fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory
}