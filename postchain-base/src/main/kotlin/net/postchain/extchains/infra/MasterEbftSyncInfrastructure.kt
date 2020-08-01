// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.extchains.infra

import net.postchain.base.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.message.Message
import net.postchain.extchains.bpm.ContainerBlockchainProcess
import net.postchain.extchains.bpm.ExternalBlockchainProcess
import net.postchain.network.masterslave.master.DefaultMasterCommunicationManager
import net.postchain.network.masterslave.master.DefaultMasterConnectionManager
import net.postchain.network.masterslave.master.MasterConnectionManager
import net.postchain.network.netty2.NettyConnectorFactory

class MasterEbftSyncInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), MasterSyncInfrastructure {

    override fun init() {
        connectionManager = DefaultMasterConnectionManager(
                NettyConnectorFactory(),
                buildInternalPeerCommConfiguration(nodeConfig),
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem(),
                nodeConfig)
    }

    override fun makeSlaveBlockchainProcess(
            chainId: Long,
            blockchainRid: BlockchainRid,
            processName: BlockchainProcessName
    ): ExternalBlockchainProcess {

        val communicationManager = DefaultMasterCommunicationManager<Message>(
                nodeConfig,
                chainId,
                blockchainRid,
                peersCommunicationConfigFactory,
                connectionManager as MasterConnectionManager,
                processName
        ).apply { init() }

        return ContainerBlockchainProcess(processName, communicationManager)
    }

}