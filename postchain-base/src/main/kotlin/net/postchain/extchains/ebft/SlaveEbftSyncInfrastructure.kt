// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.extchains.ebft

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.extchains.SlaveSyncInfrastructure
import net.postchain.network.masterslave.slave.DefaultSlaveConnectionManager
import net.postchain.network.x.PeersCommunicationConfigFactory

class SlaveEbftSyncInfrastructure(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext,
        peersCommunicationConfigFactory: PeersCommunicationConfigFactory
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext,
        peersCommunicationConfigFactory
), SlaveSyncInfrastructure {

    override fun init() {
        connectionManager = DefaultSlaveConnectionManager(nodeConfig)
    }

}