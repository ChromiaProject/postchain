// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.base.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.network.masterslave.master.DefaultMasterCommunicationManager
import net.postchain.network.masterslave.master.DefaultMasterConnectionManager
import net.postchain.network.masterslave.master.MasterConnectionManager
import net.postchain.network.netty2.NettyConnectorFactory

class DefaultMasterSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), MasterSyncInfra {

    override fun init() {
        connectionManager = DefaultMasterConnectionManager(
                NettyConnectorFactory(),
                buildInternalPeerCommConfiguration(nodeConfig),
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                nodeConfig)
    }

    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid
    ): ContainerBlockchainProcess {

        val communicationManager = DefaultMasterCommunicationManager(
                nodeConfig,
                chainId,
                blockchainRid,
                peersCommConfigFactory,
                connectionManager as MasterConnectionManager,
                processName
        ).apply { init() }

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                processName,
                chainId,
                blockchainRid,
                communicationManager)
    }

}