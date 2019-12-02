package net.postchain.base

import net.postchain.core.BlockchainContext

open class BaseBlockchainContext(
        override val blockchainRID: BlockchainRid,
        override val nodeID: Int,
        override val chainID: Long,
        override val nodeRID: ByteArray?)
    : BlockchainContext

