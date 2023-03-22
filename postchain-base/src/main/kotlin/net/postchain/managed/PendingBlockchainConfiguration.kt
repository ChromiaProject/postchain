package net.postchain.managed

import net.postchain.common.types.WrappedByteArray
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv

data class PendingBlockchainConfiguration(
        val baseConfig: Gtv,
        val configHash: WrappedByteArray,
        val signers: List<PubKey>
) {
    val fullConfig: ByteArray by lazy {
        val base = baseConfig.asDict().toMutableMap()
        base["signers"] = gtv(signers.map { gtv(it.data) })
        encodeGtv(gtv(base))
    }
}

class PendingBlockchainConfigurationStatus(
        val height: Long,
        val config: PendingBlockchainConfiguration
) {

    var isBlockBuilt: Boolean = false

    override fun toString(): String =
            "PendingBlockchainConfigurationStatus(height=$height, configHash=${config.configHash}, isBlockBuilt=$isBlockBuilt)"
}
