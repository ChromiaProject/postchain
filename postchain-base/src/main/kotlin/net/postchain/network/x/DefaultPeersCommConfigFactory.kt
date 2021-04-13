package net.postchain.network.x

import net.postchain.base.*
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration

open class DefaultPeersCommConfigFactory : PeersCommConfigFactory {

    override fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration
    ): PeerCommConfiguration {
        return create(nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers)
    }

    override fun create(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            peers: List<ByteArray>
    ): PeerCommConfiguration {

        val relevantPeerMap = buildPeersMap(nodeConfig, blockchainRid, peers)

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
            peers: List<ByteArray> // signers
    ): Map<XPeerID, PeerInfo> {
        val myPeerId = XPeerID(nodeConfig.pubKeyByteArray)
        val peers0 = peers.map { XPeerID(it) }
        val peersReplicas = peers0.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }
        val blockchainReplicas = nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf()
        return nodeConfig.peerInfoMap.filterKeys {
            it in peers0 || it in peersReplicas || it in blockchainReplicas || it == myPeerId
        }
    }
}