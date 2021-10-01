// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.core.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

/**
 *
 */
object BlockchainRidFactory {

    val cryptoSystem = SECP256K1CryptoSystem()
    private val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

    /**
     * Calculates blockchain RID by the given blockchain configuration.
     *
     * @param data is the [Gtv] data of the configuration
     * @return the blockchain RID
     */
    fun calculateBlockchainRid(data: Gtv): BlockchainRid {
        // Need to calculate it the RID, and we do it the usual way (same as merkle root of block)
        val bcBinary = data.merkleHash(merkleHashCalculator)
        return BlockchainRid(bcBinary)
    }
}
