// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.api.rest.infra.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.InfrastructureFactory
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.network.common.ConnectionManager
import net.postchain.network.peer.DefaultPeerConnectionManager

open class BaseEBFTInfrastructureFactory : InfrastructureFactory {

    override fun makeConnectionManager(nodeConfigProvider: NodeConfigurationProvider): ConnectionManager {
        return DefaultPeerConnectionManager(
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem()
        )
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManualBlockchainConfigurationProvider()
    }

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext,
            connectionManager: ConnectionManager
    ): BlockchainInfrastructure {
        val syncInfra = EBFTSynchronizationInfrastructure(nodeConfigProvider, nodeDiagnosticContext, connectionManager)
        val apiInfra = BaseApiInfrastructure(nodeConfigProvider, nodeDiagnosticContext)
        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext, connectionManager)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext,
            connectionManager: ConnectionManager
    ): BlockchainProcessManager {

        return BaseBlockchainProcessManager(
                blockchainInfrastructure,
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext,
                connectionManager)
    }
}