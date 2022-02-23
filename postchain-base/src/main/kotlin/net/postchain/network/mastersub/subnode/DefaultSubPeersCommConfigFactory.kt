package net.postchain.network.mastersub.subnode

import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.network.peer.DefaultPeersCommConfigFactory

class DefaultSubPeersCommConfigFactory : DefaultPeersCommConfigFactory() {

    override fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext?
    ): PeerCommConfiguration {

        val relevantPeerMap = buildRelevantNodeInfoMap(nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers,
                historicBlockchainContext)

        return DefaultSubPeerCommConfig.build(
                relevantPeerMap,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray,
                blockchainConfig.signers
        )
    }
}