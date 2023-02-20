// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.master

import net.postchain.common.BlockchainRid
import net.postchain.network.common.ConnectionDescriptor

class MasterConnectionDescriptor(
        val bcRid: BlockchainRid
) : ConnectionDescriptor(bcRid)