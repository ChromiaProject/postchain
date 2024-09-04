// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.peer

import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid

open class XChainPeersConfiguration(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val commConfiguration: PeerCommConfiguration,
        val peerPacketHandler: PeerPacketHandler
)