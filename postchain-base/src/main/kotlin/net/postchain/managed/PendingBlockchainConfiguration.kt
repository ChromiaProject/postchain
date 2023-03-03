package net.postchain.managed

import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

data class PendingBlockchainConfiguration(
        val baseConfig: WrappedByteArray,
        val signers: List<PubKey>
) {

    private val decodedBaseConfig: Gtv by lazy {
        GtvDecoder.decodeGtv(baseConfig.data)
    }

    val baseConfigHash: ByteArray by lazy {
        decodedBaseConfig.merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
    }

    val fullConfig: ByteArray by lazy {
        val base = decodedBaseConfig.asDict().toMutableMap()
        base["signers"] = gtv(signers.map { gtv(it.data) })
        encodeGtv(gtv(base))
    }
}
