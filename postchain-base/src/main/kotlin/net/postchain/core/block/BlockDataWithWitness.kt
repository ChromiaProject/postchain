package net.postchain.core.block

open class BlockDataWithWitness(header: BlockHeader, transactions: List<ByteArray>, val witness: BlockWitness)
    : BlockData(header, transactions)