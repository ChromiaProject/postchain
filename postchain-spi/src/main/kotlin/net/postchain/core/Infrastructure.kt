// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.PostchainContext
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.network.common.ConnectionManager
import kotlin.random.Random

/**
 * Responsible blockchain process lifecycle, i.e. creating, exiting and restarting blockchain processes.
 */
interface SynchronizationInfrastructure : Shutdownable {

    /**
     * This is how a blockchain process get created.
     */
    fun create(
            engine: BlockchainEngine,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ): BlockchainProcess

    /**
     * Call this hook upon blockchain process restart.
     * Note: responsible for keeping track of the two BC process sync modes (normal sync and fastsync)
     */
    fun restart(process: BlockchainProcess)

    /**
     * Call this hook before blockchain process is killed.
     * Note: responsible for keeping track of the two BC process sync modes (normal sync and fastsync)
     */
    fun terminate(process: BlockchainProcess)
}

fun interface BlockchainRestartNotifier {
    fun notifyRestart(loadNextPendingConfig: Boolean)
}

interface BlockchainInfrastructure : Shutdownable {

    /**
     * Creates the blockchain [SynchronizationInfrastructure] and connects any [SynchronizationInfrastructureExtension].
     * Also connects the process to the [ApiInfrastructure].
     *
     * @return A new blockchain process created by the [SynchronizationInfrastructure]
     */
    fun createBlockchainProcess(
            engine: BlockchainEngine,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ): BlockchainProcess

    /**
     * Restarts the blockchain [SynchronizationInfrastructure] and any [SynchronizationInfrastructureExtension].
     * Also notifies [ApiInfrastructure] of the restart.
     *
     * @param process The blockchain process that is restarting.
     */
    fun handleBlockchainRestart(process: BlockchainProcess)

    /**
     * Terminates the blockchain [SynchronizationInfrastructure] and any [SynchronizationInfrastructureExtension].
     * Also notifies [ApiInfrastructure] of the termination.
     *
     * @param process The blockchain process that is terminating.
     */
    fun handleBlockchainTermination(process: BlockchainProcess)

    fun makeBlockchainConfiguration(
            rawConfigurationData: ByteArray,
            eContext: EContext,
            nodeId: Int,
            chainId: Long,
            bcConfigurationFactory: BlockchainConfigurationFactorySupplier,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): BlockchainConfiguration

    fun makeBlockchainEngine(
            configuration: BlockchainConfiguration,
            beforeCommitHandler: BeforeCommitHandler,
            afterCommitHandler: AfterCommitHandler,
            blockBuilderStorage: Storage,
            sharedStorage: Storage,
            initialEContext: EContext,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier
    ): BlockchainEngine

}

/**
 * This interface works a bit like a lifecycle hook, basically you can create a chunk of logic that can use
 * a [BlockchainProcess] for something during startup of the process.
 *
 * To see how it all goes together, see: doc/extension_classes.graphml
 *
 */
interface BlockchainProcessConnectable {
    /**
     * "connect" here is a loosely defined concept. Often we want to initiate the corresponding [GTXSpecialTxExtension]
     * during "connect" but it could be anything.
     *
     * @param process is the new process being created.
     */
    fun connectProcess(process: BlockchainProcess)

    fun disconnectProcess(process: BlockchainProcess)
}

interface RemoteBlockchainProcessConnectable {
    fun connectRemoteProcess(process: RemoteBlockchainProcess)
    fun disconnectRemoteProcess(process: RemoteBlockchainProcess)
}

/**
 * NOTE: Remember that the Sync Infra Extension is just a part of many extension interfaces working together
 * (examples: BBB Ext and GTX Spec TX Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 */
interface SynchronizationInfrastructureExtension : BlockchainProcessConnectable, Shutdownable

interface ApiInfrastructure : BlockchainProcessConnectable, Shutdownable {
    fun restartProcess(process: BlockchainProcess)
}

interface BlockchainProcessManagerExtension : BlockchainProcessConnectable, Shutdownable {
    fun afterCommit(process: BlockchainProcess, height: Long)
}

interface InfrastructureFactory {
    fun makeNodeConfigurationProvider(appConfig: AppConfig, storage: Storage): NodeConfigurationProvider

    fun makeConnectionManager(nodeConfigProvider: NodeConfigurationProvider, random: Random): ConnectionManager

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

    // Container chains
    EbftManagedContainerMaster("ebft-managed-container-master"),
    EbftContainerSub("ebft-container-sub"),
    ;

    fun get(): String = key.first()
}

interface InfrastructureFactoryProvider {
    fun createInfrastructureFactory(appConfig: AppConfig): InfrastructureFactory
}
