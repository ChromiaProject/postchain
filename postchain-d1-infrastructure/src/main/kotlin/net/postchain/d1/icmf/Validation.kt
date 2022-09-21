package net.postchain.d1.icmf

import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockWitnessBuilder
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.core.block.BlockHeader
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.getBFTRequiredSignatureCount

object Validation {
    fun validateBlockSignatures(cryptoSystem: CryptoSystem,
                                previousBlockRid: ByteArray,
                                rawHeader: ByteArray,
                                blockRid: Hash,
                                peers: List<PubKey>,
                                witness: BaseBlockWitness): Boolean {
        val blockWitnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, object : BlockHeader {
            override val prevBlockRID = previousBlockRid
            override val rawData = rawHeader
            override val blockRID = blockRid
        }, peers.map { it.key }.toTypedArray(), getBFTRequiredSignatureCount(peers.size))

        for (signature in witness.getSignatures()) {
            try {
                blockWitnessBuilder.applySignature(signature)
            } catch (e: UserMistake) {
                return false
            }
        }

        if (!blockWitnessBuilder.isComplete()) {
            return false
        }
        return true
    }
}
