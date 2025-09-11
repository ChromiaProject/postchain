package net.postchain.managed

import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.BlockchainConfiguration
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv

data class PendingBlockchainConfiguration(
        val baseConfig: Gtv,
        val configHash: WrappedByteArray,
        val signers: List<PubKey>,
        val minimumHeight: Long
) {
    val fullConfig: ByteArray by lazy {
        val base = baseConfig.asDict().toMutableMap()
        base["signers"] = gtv(signers.map { gtv(it.data) })
        encodeGtv(gtv(base))
    }

    companion object {
        fun fromBlockchainConfiguration(blockchainConfiguration: BlockchainConfiguration, minimumHeight: Long): PendingBlockchainConfiguration =
                PendingBlockchainConfiguration(
                        blockchainConfiguration.rawConfig,
                        blockchainConfiguration.configHash.wrap(),
                        blockchainConfiguration.signers.map { PubKey(it) },
                        minimumHeight
                )
    }
}
