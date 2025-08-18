package net.postchain.core.block

interface BlockHeaderWithWitness {
    val header: BlockHeader
    val witness: BlockWitness
}
