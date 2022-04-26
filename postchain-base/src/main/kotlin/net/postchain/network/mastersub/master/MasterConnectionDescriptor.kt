// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid
import net.postchain.network.common.ConnectionDescriptor
import net.postchain.network.mastersub.protocol.MsHandshakeMessage

class MasterConnectionDescriptor(
        val bcRid: BlockchainRid
): ConnectionDescriptor(bcRid) {
    companion object Factory {
        // TODO: [POS-129]: Maybe delete it
        fun createFromHandshake(message: MsHandshakeMessage): MasterConnectionDescriptor {
            return MasterConnectionDescriptor(BlockchainRid(message.blockchainRid))
        }
    }
}
