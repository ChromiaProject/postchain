// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.core.BlockchainRid
import net.postchain.base.PeerCommConfiguration

open class XChainPeersConfiguration(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val commConfiguration: PeerCommConfiguration,
        val packetHandler: XPacketHandler
)