// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.master

import net.postchain.base.BlockchainRid
import net.postchain.network.masterslave.protocol.HandshakeMsMessage

class MasterConnectionDescriptor(
        val blockchainRid: BlockchainRid
) {
    companion object Factory {
        // TODO: [POS-129]: Maybe delete it
        fun createFromHandshake(message: HandshakeMsMessage): MasterConnectionDescriptor {
            return MasterConnectionDescriptor(BlockchainRid(message.blockchainRid))
        }
    }
}
