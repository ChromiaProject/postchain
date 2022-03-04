// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.containers.bpm.PostchainContainer
import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.heartbeat.Chain0HeartbeatListener
import net.postchain.ebft.heartbeat.DefaultHeartbeatListener
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.mastersub.master.DefaultMasterCommunicationManager
import net.postchain.network.mastersub.master.MasterCommunicationManager
import net.postchain.network.mastersub.master.MasterConnectionManager
import net.postchain.network.mastersub.master.MasterConnectionManagerFactory
import java.nio.file.Path


open class DefaultMasterSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), MasterSyncInfra {

    lateinit var masterConnectionManager: MasterConnectionManager

    override fun init() {
        val masterFactory = MasterConnectionManagerFactory(
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem(),
                nodeConfig
        )
        masterConnectionManager = masterFactory.getMasterConnectionManager()
        connectionManager = masterFactory.getPeerConnectionManager()

        fillDiagnosticContext()
    }

    /**
     * We create a new [MasterCommunicationManager] for every new BC process we make.
     */
    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            targetContainer: PostchainContainer,
            containerChainDir: Path
    ): ContainerBlockchainProcess {

        if (!::masterConnectionManager.isInitialized) {
            throw ProgrammerMistake("Cannot create BC process before we have called init() on the DefaultMasterSyncInfra.")
        }

        val communicationManager = DefaultMasterCommunicationManager(
                nodeConfig,
                chainId,
                blockchainRid,
                peersCommConfigFactory,
                connectionManager,
                masterConnectionManager,
                dataSource,
                processName
        ).apply { init() }

        val unregisterBlockchainDiagnosticData: () -> Unit = {
            blockchainProcessesDiagnosticData.remove(blockchainRid)
        }

        val process = DefaultContainerBlockchainProcess(
                nodeConfig,
                processName,
                chainId,
                blockchainRid,
                targetContainer.restApiPort,
                communicationManager,
                dataSource,
                containerChainDir,
                unregisterBlockchainDiagnosticData
        )

        blockchainProcessesDiagnosticData[blockchainRid] = mutableMapOf<String, () -> Any>(
                DiagnosticProperty.BLOCKCHAIN_RID.prettyName to { blockchainRid.toHex() },
                DiagnosticProperty.CONTAINER_NAME.prettyName to { targetContainer.containerName.toString() },
                DiagnosticProperty.CONTAINER_ID.prettyName to { targetContainer.shortContainerId() ?: "" }
        )

        return process
    }

    override fun exitMasterBlockchainProcess(process: ContainerBlockchainProcess) = Unit

    override fun shutdown() {
        super.shutdown()
        masterConnectionManager.shutdown()
    }

    override fun makeHeartbeatListener(chainId: Long, blockchainRid: BlockchainRid): HeartbeatListener {
        return if (chainId == 0L) Chain0HeartbeatListener()
        else DefaultHeartbeatListener(nodeConfig, chainId)
    }
}