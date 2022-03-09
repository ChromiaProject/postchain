// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.api.DefaultMasterApiInfra
import net.postchain.containers.bpm.ContainerManagedBlockchainProcessManager
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.message.Message
import net.postchain.managed.ManagedEBFTInfrastructureFactory
import net.postchain.network.mastersub.master.MasterConnectionManagerFactory

open class MasterManagedEbftInfraFactory : ManagedEBFTInfrastructureFactory() {
    lateinit var masterFactory: MasterConnectionManagerFactory<Message>

    protected fun getOrCreateMasterFactory(nodeConfig: NodeConfig): MasterConnectionManagerFactory<Message> {
        if (!::masterFactory.isInitialized) {
            masterFactory = MasterConnectionManagerFactory(
                            EbftPacketEncoderFactory(),
                            EbftPacketDecoderFactory(),
                            SECP256K1CryptoSystem(),
                            nodeConfig)
        }
        return masterFactory
    }

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val masterFactory = getOrCreateMasterFactory(nodeConfigProvider.getConfiguration())
        val syncInfra = DefaultMasterSyncInfra(
                nodeConfigProvider, nodeDiagnosticContext, masterFactory.getMasterConnectionManager(), masterFactory.getPeerConnectionManager())

        val apiInfra = DefaultMasterApiInfra(
                nodeConfigProvider, nodeDiagnosticContext)

        return DefaultMasterBlockchainInfra(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext, connectionManager)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {

        val masterFactory = getOrCreateMasterFactory(nodeConfigProvider.getConfiguration())
        return ContainerManagedBlockchainProcessManager(
                blockchainInfrastructure as MasterBlockchainInfra,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext,
                masterFactory.getPeerConnectionManager()
        )
    }
}