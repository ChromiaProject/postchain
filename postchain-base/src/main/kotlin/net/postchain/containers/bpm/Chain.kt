package net.postchain.containers.bpm

import net.postchain.common.BlockchainRid

class Chain(
        val chainId: Long,
        val brid: BlockchainRid,
        val containers: List<ContainerName>
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chain

        return chainId == other.chainId
    }

    override fun hashCode(): Int {
        return chainId.hashCode()
    }

    override fun toString(): String {
        return "[${containers.joinToString(",")}]/$chainId/${brid.toShortHex()}"
    }
}