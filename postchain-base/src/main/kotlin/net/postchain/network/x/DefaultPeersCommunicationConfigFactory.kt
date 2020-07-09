package net.postchain.network.x

import net.postchain.base.*
import net.postchain.config.node.NodeConfig

open class DefaultPeersCommunicationConfigFactory : PeersCommunicationConfigFactory {

    override fun create(
            nodeConfig: NodeConfig,
            chainId: Long,
            blockchainRid: BlockchainRid,
            signers: List<ByteArray>
    ): PeerCommConfiguration {

        val relevantPeerMap = buildPeersMap(nodeConfig, blockchainRid, signers)

        return BasePeerCommConfiguration.build(
                relevantPeerMap,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray)
    }

    protected fun buildPeersMap(
            nodeConfig: NodeConfig,
            blockchainRid: BlockchainRid,
            signers: List<ByteArray>
    ): Map<XPeerID, PeerInfo> {
        val myPeerId = XPeerID(nodeConfig.pubKeyByteArray)
        val signers0 = signers.map { XPeerID(it) }
        val signersReplicas = signers0.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }
        val blockchainReplicas = nodeConfig.blockchainReplicaNodes[blockchainRid] ?: listOf()
        return nodeConfig.peerInfoMap.filterKeys {
            it in signers0 || it in signersReplicas || it in blockchainReplicas || it == myPeerId
        }
    }
}