package net.postchain.base.data

import java.math.BigInteger


enum class TypeOfSystemQuery {
    block, transaction
}

// Question: does it make sense to have a typealias?
typealias Hex = String

// Question: Set blockchainRID here?
data class BaseExplorerQuery(
        val component: TypeOfSystemQuery,
        val from: BigInteger?,
        val to: BigInteger?,
        val publicKey: Array<Hex>?
        )


// Question: make an interface of all this stuff?
data class BaseBlockExplorerResponse (
        val blockchainRID: Hex,
        val blockRID: Hex,
        val blockHeight: Long,
        val blockHeader: Hex,
        val blockWitness: Hex,
        val timestamp: Long
)