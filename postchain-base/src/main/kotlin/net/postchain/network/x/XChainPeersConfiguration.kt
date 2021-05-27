// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.ebft.heartbeat.HeartbeatEvent

open class XChainPeersConfiguration(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val commConfiguration: PeerCommConfiguration,
        val packetHandler: XPacketHandler,
        val heartbeatHandler: (HeartbeatEvent) -> Unit = {}
)