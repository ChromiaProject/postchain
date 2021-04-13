package net.postchain.network.x

import net.postchain.base.*
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration

open class DefaultPeersCommConfigFactory : PeersCommConfigFactory {

    override fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchain: HistoricBlockchain?
    ): PeerCommConfiguration {
        return create(nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers, historicBlockchain)
    }

    override fun create(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>,
            historicBlockchain: HistoricBlockchain?
    ): PeerCommConfiguration {

        val relevantPeerMap = buildPeersMap(nodeConfig, blockchainRid, peers, historicBlockchain)

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray
        )
    }

    protected fun buildPeersMap(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>, // signers
            historicBlockchain: HistoricBlockchain?
    ): Map<XPeerID, PeerInfo> {
        val myPeerId = XPeerID(nodeConfig.pubKeyByteArray)
        val peers0 = peers.map { XPeerID(it) }
        val peersReplicas = peers0.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }

        val blockchainReplicas = if (historicBlockchain != null) {
            (nodeConfig.blockchainReplicaNodes[historicBlockchain.historicBrid] ?: listOf()).union(
                    nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf())
        } else {
            nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf()
        }

        return nodeConfig.peerInfoMap.filterKeys {
            it in peers0 || it in peersReplicas || it in blockchainReplicas || it == myPeerId
        }
    }
}