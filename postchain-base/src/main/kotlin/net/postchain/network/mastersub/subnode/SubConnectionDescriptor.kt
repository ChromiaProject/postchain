// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode

import net.postchain.common.BlockchainRid
import net.postchain.network.common.ConnectionDescriptor

/**
 * This descriptor has a list of all other subnodes.
 */
data class SubConnectionDescriptor(
        private val bcRid: BlockchainRid,
        val peers: List<ByteArray>
) : ConnectionDescriptor(bcRid)
