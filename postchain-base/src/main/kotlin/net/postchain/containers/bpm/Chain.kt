package net.postchain.containers.bpm

import net.postchain.common.BlockchainRid

internal class Chain(
        val containerName: ContainerName,
        val chainId: Long,
        val brid: BlockchainRid
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chain

        if (chainId != other.chainId) return false

        return true
    }

    override fun hashCode(): Int {
        return chainId.hashCode()
    }

    override fun toString(): String {
        return "$containerName/$chainId/${brid.toShortHex()}"
    }
}