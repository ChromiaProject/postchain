package net.postchain.gtx

import net.postchain.base.configuration.KEY_GTX_ALLOWOVERRIDES
import net.postchain.base.configuration.KEY_GTX_MAX_TX_SIGNATURES
import net.postchain.base.configuration.KEY_GTX_MODULES
import net.postchain.base.configuration.KEY_GTX_SLOW_OP_THRESHOLD
import net.postchain.base.configuration.KEY_GTX_TX_SIZE
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.Nullable
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class GtxConfigurationData(
        @RawGtv
        val rawGtv: Gtv,
        @Name(KEY_GTX_TX_SIZE)
        @DefaultValue(defaultLong = (25 * 1024 * 1024)) // 25 mb
        val maxTxSize: Long,
        @Name(KEY_GTX_MAX_TX_SIGNATURES)
        @DefaultValue(defaultLong = 100)
        val maxTxSignatures: Long,
        @Name(KEY_GTX_MODULES)
        @Nullable
        private val modulesRaw: List<String>?,
        @Name(KEY_GTX_ALLOWOVERRIDES)
        @DefaultValue(defaultBoolean = false)
        val allowOverrides: Boolean,
        @Name(KEY_GTX_SLOW_OP_THRESHOLD)
        @DefaultValue(defaultLong = -1)
        val slowOpThreshold: Long,
) {
    val modules = modulesRaw ?: listOf()

    companion object {
        @JvmStatic
        val default = gtv(mapOf()).toObject<GtxConfigurationData>()
    }
}
