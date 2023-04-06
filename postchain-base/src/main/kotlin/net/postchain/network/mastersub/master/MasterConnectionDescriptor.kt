// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid

class MasterConnectionDescriptor(
        val blockchainRid: BlockchainRid?,
        val containerIID: Int
)