// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.core.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.bpm.ContainerBlockchainProcess
import net.postchain.containers.bpm.ContainerChainDir
import net.postchain.containers.bpm.DefaultContainerBlockchainProcess
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.managed.DirectoryDataSource
import net.postchain.network.masterslave.master.DefaultMasterCommunicationManager
import net.postchain.network.masterslave.master.DefaultMasterConnectionManager
import net.postchain.network.masterslave.master.MasterConnectionManager
import net.postchain.network.netty2.NettyConnectorFactory

open class DefaultMasterSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext
), MasterSyncInfra {

    override fun init() {
        connectionManager = DefaultMasterConnectionManager(
                NettyConnectorFactory(),
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem(),
                nodeConfig
        )
    }

    override fun makeMasterBlockchainProcess(
            processName: BlockchainProcessName,
            chainId: Long,
            blockchainRid: BlockchainRid,
            dataSource: DirectoryDataSource,
            containerChainDir: ContainerChainDir,
            restApiPort: Int
    ): ContainerBlockchainProcess {

        val communicationManager = DefaultMasterCommunicationManager(
                nodeConfig,
                chainId,
                blockchainRid,
                peersCommConfigFactory,
                connectionManager as MasterConnectionManager,
                dataSource,
                processName
        ).apply { init() }

        return DefaultContainerBlockchainProcess(
                nodeConfig,
                processName,
                chainId,
                blockchainRid,
                restApiPort,
                communicationManager,
                dataSource,
                containerChainDir
        )
    }

}