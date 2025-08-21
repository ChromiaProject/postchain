package net.postchain.core.block

data class BlockHeaderWithWitness(
    val header: BlockHeader,
    val witness: BlockWitness
)
