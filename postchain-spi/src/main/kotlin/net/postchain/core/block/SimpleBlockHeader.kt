package net.postchain.core.block

class SimpleBlockHeader(
        override val prevBlockRID: ByteArray,
        override val rawData: ByteArray,
        override val blockRID: ByteArray) : BlockHeader