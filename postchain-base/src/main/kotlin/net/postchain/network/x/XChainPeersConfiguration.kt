// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.x

import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.network.XPacketDecoder
import net.postchain.network.XPacketEncoder

/* TODO: [et][POS-129]: merge with PeerCommConfiguration */
open class XChainPeersConfiguration(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val commConfiguration: PeerCommConfiguration, // TODO: Rename it
        val packetHandler: XPacketHandler,
        val packetEncoder: XPacketEncoder<*>,
        val packetDecoder: XPacketDecoder<*>
)