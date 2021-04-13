package net.postchain.network.masterslave.slave

import net.postchain.base.HistoricBlockchain
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.network.x.DefaultPeersCommConfigFactory

class DefaultSlavePeersCommConfigFactory : DefaultPeersCommConfigFactory() {

    override fun create(
            nodeConfig: NodeConfig,
            blockchainConfig: BlockchainConfiguration,
            historicBlockchain: HistoricBlockchain?
    ): PeerCommConfiguration {

        val relevantPeerMap = buildPeersMap(nodeConfig, blockchainConfig.blockchainRid, blockchainConfig.signers, historicBlockchain)

        return DefaultSlavePeerCommConfig.build(
                relevantPeerMap,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray,
                blockchainConfig.signers
        )
    }
}