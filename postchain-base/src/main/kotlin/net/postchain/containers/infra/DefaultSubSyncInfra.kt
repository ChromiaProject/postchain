// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.containers.infra

import net.postchain.core.BlockchainRid
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.heartbeat.HeartbeatChecker
import net.postchain.ebft.heartbeat.RemoteConfigChecker
import net.postchain.network.mastersub.subnode.DefaultSubConnectionManager
import net.postchain.network.mastersub.subnode.SubConnectionManager
import net.postchain.network.peer.PeersCommConfigFactory

class DefaultSubSyncInfra(
        nodeConfigProvider: NodeConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext,
        peersCommConfigFactory: PeersCommConfigFactory
) : EBFTSynchronizationInfrastructure( // TODO: Olle: Do we need this?
        nodeConfigProvider,
        nodeDiagnosticContext,
        peersCommConfigFactory
), SubSyncInfra {

    override fun init() {
        connectionManager = DefaultSubConnectionManager(nodeConfig) // TODO: Olle: this is no longer a PeerConnectionManager
    }

    override fun makeHeartbeatChecker(chainId: Long, blockchainRid: BlockchainRid): HeartbeatChecker {
        return RemoteConfigChecker(nodeConfig, chainId, blockchainRid, connectionManager as SubConnectionManager)
    }
}