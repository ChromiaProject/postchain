// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.network.mastersub.subnode

import net.postchain.common.BlockchainRid

/**
 * This descriptor has a list of all other subnodes.
 */
data class SubConnectionDescriptor(
        val blockchainRid: BlockchainRid?,
        val peers: List<ByteArray>,
        val containerIID: Int
)
