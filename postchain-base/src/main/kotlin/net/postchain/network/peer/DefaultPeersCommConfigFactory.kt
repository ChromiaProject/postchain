package net.postchain.network.peer

import net.postchain.base.*
import net.postchain.config.app.AppConfig
import net.postchain.core.NodeRid
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.common.BlockchainRid

open class DefaultPeersCommConfigFactory : PeersCommConfigFactory {

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {
        return create(appConfig, nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers, historicBlockchainContext)
    }

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {

        val relevantPeerMap = buildRelevantNodeInfoMap(appConfig, nodeConfig, blockchainRid, peers, historicBlockchainContext)

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                SECP256K1CryptoSystem(),
                appConfig.privKeyByteArray,
                appConfig.pubKeyByteArray
        )
    }

    /**
     * The [NodeConfig] has knowledge of many postchain nodes, but we want to narrow this list down to only
     * contain nodes that can be useful for the given blockchain.
     *
     * @param nodeConfig
     * @param blockchainRid is the chain we are interested in
     * @param peers are the signers/block builders found in the BC's configuration.
     * @param historicBlockchainContext is used to get replicas
     *
     * @return a map from [NodeRid] to [PeerInfo] containing as many nodes we can ever have any use for w/ regards
     *         to the given BC. Only nodes that are obviously useless are removed.
     */
    protected fun buildRelevantNodeInfoMap(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>, // signers
            historicBlockchainContext: HistoricBlockchainContext?
    ): Map<NodeRid, PeerInfo> {
        val myNodeRid = NodeRid(appConfig.pubKeyByteArray)
        val peers0 = peers.map { NodeRid(it) }
        val peersReplicas = peers0.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }

        val blockchainReplicas = if (historicBlockchainContext != null) {
            (nodeConfig.blockchainReplicaNodes[historicBlockchainContext.historicBrid] ?: listOf()).union(
                    nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf())
        } else {
            nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf()
        }

        // We keep
        // 1. All BC's peers
        // 2. All FULL node replicas
        // 3. All nodes that replicates the BC
        // 4. This node itself
        return nodeConfig.peerInfoMap.filterKeys {
            it in peers0 || it in peersReplicas || it in blockchainReplicas || it == myNodeRid
        }
    }
}