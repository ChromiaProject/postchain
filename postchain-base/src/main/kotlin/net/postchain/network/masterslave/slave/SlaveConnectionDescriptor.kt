// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.masterslave.slave

import net.postchain.base.BlockchainRid

class SlaveConnectionDescriptor(
        val blockchainRid: BlockchainRid,
        val singers: List<ByteArray>
)
