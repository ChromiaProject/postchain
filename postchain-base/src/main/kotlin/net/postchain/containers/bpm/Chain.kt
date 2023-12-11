package net.postchain.containers.bpm

import net.postchain.common.BlockchainRid

class Chain(
        val chainId: Long,
        val brid: BlockchainRid,
        val containerName: ContainerName,
        var restApiEnabled: Boolean = true
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chain

        if (chainId != other.chainId) return false
        if (brid != other.brid) return false
        if (containerName != other.containerName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chainId.hashCode()
        result = 31 * result + brid.hashCode()
        result = 31 * result + containerName.hashCode()
        return result
    }

    override fun toString(): String {
        return "$containerName/$chainId/${brid.toShortHex()}"
    }
}