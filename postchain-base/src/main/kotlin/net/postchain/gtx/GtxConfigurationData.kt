package net.postchain.gtx

import net.postchain.base.KEY_GTX_ALLOWOVERRIDES
import net.postchain.base.KEY_GTX_MODULES
import net.postchain.base.KEY_GTX_SQL_MODULES
import net.postchain.base.KEY_GTX_TX_SIZE
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.*

data class GtxConfigurationData(
        @RawGtv
        val rawGtv: Gtv,
        @Name(KEY_GTX_TX_SIZE)
        @DefaultValue(defaultLong = (25 * 1024 * 1024)) // 25 mb
        val maxTxSize: Long,
        @Name(KEY_GTX_MODULES)
        @Nullable
        private val modulesRaw: List<String>?,
        @Name(KEY_GTX_SQL_MODULES)
        @Nullable
        private val sqlModulesRaw: List<String>?,
        @Name(KEY_GTX_ALLOWOVERRIDES)
        @DefaultValue(defaultBoolean = false)
        val allowOverrides: Boolean
) {
    val modules = modulesRaw ?: listOf()
    val sqlModules = sqlModulesRaw ?: listOf()

    companion object {
        @JvmStatic
        val default = gtv(mapOf()).toObject<GtxConfigurationData>()
    }
}
