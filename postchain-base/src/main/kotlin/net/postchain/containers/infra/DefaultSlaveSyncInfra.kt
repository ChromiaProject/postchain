// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.core.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.heartbeat.HeartbeatChecker
import net.postchain.ebft.heartbeat.RemoteConfigChecker
import net.postchain.network.masterslave.slave.DefaultSlaveConnectionManager
import net.postchain.network.masterslave.slave.SlaveConnectionManager
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

    override fun makeHeartbeatChecker(chainId: Long, blockchainRid: BlockchainRid): HeartbeatChecker {
        return RemoteConfigChecker(nodeConfig, chainId, blockchainRid, connectionManager as SlaveConnectionManager)
    }
}