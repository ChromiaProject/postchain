package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.configuration.KEY_SNAPSHOT_INTERVAL
import net.postchain.base.configuration.KEY_SNAPSHOT_LEVELS_PER_PAGE
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class SnapshotBlockchainConfigurationData(
    @RawGtv
    val rawGtv: Gtv,

    @Name(KEY_SNAPSHOT_INTERVAL)
    @DefaultValue(defaultLong = 100) // TODO: What is reasonable?
    val snapshotInterval: Long,

    @Name(KEY_SNAPSHOT_LEVELS_PER_PAGE)
    @DefaultValue(defaultLong = 2) // TODO: What is reasonable? Skip default to make owners decide?
    private val _levelsPerPage: Long
) {
    val levelsPerPage: Int
        get() = _levelsPerPage.toInt()

    companion object : KLogging() {
        @JvmStatic
        val default = GtvFactory.gtv(mapOf()).toObject<SnapshotBlockchainConfigurationData>()
    }
}
