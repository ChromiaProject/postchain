// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.network.masterslave.slave.DefaultSlaveConnectionManager
import net.postchain.network.x.PeersCommConfigFactory

class DefaultSlaveSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext,
        peersCommConfigFactory: PeersCommConfigFactory
) : EBFTSynchronizationInfrastructure(
        nodeConfigProvider,
        nodeDiagnosticContext,
        peersCommConfigFactory
), SlaveSyncInfra {

    override fun init() {
        connectionManager = DefaultSlaveConnectionManager(nodeConfig)
    }

}