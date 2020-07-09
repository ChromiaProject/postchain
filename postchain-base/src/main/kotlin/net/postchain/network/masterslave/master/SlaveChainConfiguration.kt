package net.postchain.network.masterslave.master

import net.postchain.base.BlockchainRid
import net.postchain.network.masterslave.PacketHandler

open class SlaveChainConfiguration(
        val chainId: Long,
        val blockchainRid: BlockchainRid,
        val packetHandler: PacketHandler
)
