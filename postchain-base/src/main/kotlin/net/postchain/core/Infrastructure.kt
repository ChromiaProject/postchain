// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.network.common.ConnectionManager

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
            heartbeatListener: HeartbeatListener?
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

/**
 * This interface works a bit like a lifecycle hook, basically you can create a chunk of logic that can use
 * a [BlockchainProcess] for something during startup of the process.
 *
 * To see how it all goes together, see: doc/extension_classes.graphml
 *
 */
interface BlockchainProcessLifecycleHandler: Shutdownable {
    /**
     * "connect" here is a loosely defined concept. Often we want to initiate the corresponding [GTXSpecialTxExtension]
     * during "connect" but it could be anything.
     *
     * @param process is the new process being created.
     */
    fun connectProcess(process: BlockchainProcess)

    fun disconnectProcess(process: BlockchainProcess)
}

interface SynchronizationInfrastructureExtension: BlockchainProcessLifecycleHandler

interface ApiInfrastructure: BlockchainProcessLifecycleHandler

interface BlockchainProcessManagerExtension: BlockchainProcessLifecycleHandler {
    fun afterCommit(process: BlockchainProcess, height: Long)
}

interface InfrastructureFactory {

    fun makeConnectionManager(nodeConfigProvider: NodeConfigurationProvider): ConnectionManager

    fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider

    fun makeBlockchainInfrastructure(postchainContext: PostchainContext): BlockchainInfrastructure

    fun makeProcessManager(postchainContext: PostchainContext,
                           blockchainInfrastructure: BlockchainInfrastructure,
                           blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager
}

enum class Infrastructure(vararg val key: String) {
    Ebft("ebft", "base-ebft", "base/ebft"),
    EbftManaged("ebft-managed", "net.postchain.managed.ManagedEBFTInfrastructureFactory"), // compatibility
    EbftManagedChromia0("chromia0", "ebft-managed-chromia0", "net.postchain.managed.Chromia0InfrastructureFactory"), // compatibility

    // Container chains
    EbftManagedContainerMaster("ebft-managed-container-master"),
    EbftContainerSub("ebft-container-sub"),
    EbftManagedChromia0ContainerMaster("ebft-managed-chromia0-container-master"),

    // Tests
    BaseTest("base-test", "base/test");

    fun get(): String = key.first()
}

interface InfrastructureFactoryProvider {
    fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory
}