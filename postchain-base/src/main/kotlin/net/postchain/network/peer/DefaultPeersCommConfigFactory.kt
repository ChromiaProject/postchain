package net.postchain.network.peer

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.PeerInfo
import net.postchain.common.BlockchainRid
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.NodeRid

open class DefaultPeersCommConfigFactory : PeersCommConfigFactory {

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {
        return create(appConfig, nodeConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, blockchainConfig.signers, historicBlockchainContext)
    }

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {

        val relevantPeerMap = buildRelevantNodeInfoMap(appConfig, nodeConfig, chainId, blockchainRid, peers, historicBlockchainContext)

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                appConfig
        )
    }

    override fun create(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            ancestorBlockchainRid: BlockchainRid,
            historicBlockchainContext: HistoricBlockchainContext
    ): PeerCommConfiguration {
        val myNodeRid = NodeRid(appConfig.pubKeyByteArray)
        val peersThatServeAncestorBrid = historicBlockchainContext.ancestors[ancestorBlockchainRid] ?: emptySet()

        val relevantPeerMap = nodeConfig.peerInfoMap.filterKeys {
            it in peersThatServeAncestorBrid || it == myNodeRid
        }

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                appConfig
        )
    }

    /**
     * The [NodeConfig] has knowledge of many postchain nodes, but we want to narrow this list down to only
     * contain nodes that can be useful for the given blockchain.
     *
     * @param nodeConfig
     * @param blockchainRid is the chain we are interested in
     * @param signers are the signers/block builders found in the BC's configuration.
     * @param historicBlockchainContext is used to get replicas
     *
     * @return a map from [NodeRid] to [PeerInfo] containing as many nodes we can ever have any use for w/ regards
     *         to the given BC. Only nodes that are obviously useless are removed.
     */
    protected fun buildRelevantNodeInfoMap(
            appConfig: AppConfig,
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            signers: List<ByteArray>, // signers
            historicBlockchainContext: HistoricBlockchainContext?
    ): Map<NodeRid, PeerInfo> {
        val myNodeRid = NodeRid(appConfig.pubKeyByteArray)
        val signers0 = signers.map { NodeRid(it) }

        val blockchainReplicas = if (historicBlockchainContext != null) {
            (getBlockchainReplicaNodes(nodeConfig, historicBlockchainContext.historicBrid, chainId) ?: listOf()).union(
                    getBlockchainReplicaNodes(nodeConfig, blockchainRid, chainId) ?: listOf())
        } else {
            getBlockchainReplicaNodes(nodeConfig, blockchainRid, chainId) ?: listOf()
        }

        // We keep
        // 1. All BC's signers
        // 2. All nodes that replicate the BC (if must sync until height is set, otherwise just locally configured replicas)
        // 3. This node itself
        return nodeConfig.peerInfoMap.filterKeys {
            it in signers0 || it in blockchainReplicas || it == myNodeRid
        }
    }

    /**
     * If "must sync until" is set we will add all known blockchain replica nodes as peers,
     * otherwise only locally configured replica nodes will be considered.
     *
     * NOTE: This could be made smarter, perhaps some replica nodes should be included in some cases
     */
    private fun getBlockchainReplicaNodes(nodeConfig: NodeConfig, blockchainRid: BlockchainRid, chainId: Long): List<NodeRid>? {
        val mustSyncUntil = nodeConfig.mustSyncUntilHeight?.get(chainId) ?: -1
        return if (mustSyncUntil > -1) {
            nodeConfig.blockchainReplicaNodes[blockchainRid]
        } else {
            nodeConfig.locallyConfiguredBlockchainReplicaNodes[blockchainRid]
        }
    }
}