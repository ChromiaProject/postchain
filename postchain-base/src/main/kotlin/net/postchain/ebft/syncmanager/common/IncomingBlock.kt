// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.syncmanager.common

import net.postchain.core.block.BlockDataWithWitness

data class IncomingBlock(val block: BlockDataWithWitness, val height: Long) : Comparable<IncomingBlock> {
    override fun compareTo(other: IncomingBlock) = height.compareTo(other.height)
}
