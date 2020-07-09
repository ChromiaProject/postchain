// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.extchains.ebft

import net.postchain.base.BaseApiInfrastructure
import net.postchain.base.BaseBlockchainInfrastructure
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.extchains.MasterBlockchainInfrastructure
import net.postchain.extchains.bpm.MasterManagedBlockchainProcessManager
import net.postchain.managed.ManagedEBFTInfrastructureFactory

class MasterExtChainsEbftInfrastructureFactory : ManagedEBFTInfrastructureFactory() {

    override fun makeBlockchainInfrastructure(
            nodeConfigProvider: NodeConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainInfrastructure {

        val syncInfra = MasterEbftSyncInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext)

        val apiInfra = BaseApiInfrastructure(
                nodeConfigProvider, nodeDiagnosticContext)

        return BaseBlockchainInfrastructure(
                nodeConfigProvider, syncInfra, apiInfra, nodeDiagnosticContext)
    }

    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            nodeDiagnosticContext: NodeDiagnosticContext
    ): BlockchainProcessManager {

        return MasterManagedBlockchainProcessManager(
                blockchainInfrastructure as MasterBlockchainInfrastructure, // TODO: [POS-129]: Improve it
                nodeConfigProvider,
                blockchainConfigurationProvider,
                nodeDiagnosticContext)
    }
}