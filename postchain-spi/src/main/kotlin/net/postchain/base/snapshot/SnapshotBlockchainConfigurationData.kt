package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.configuration.KEY_SNAPSHOT_INTERVAL
import net.postchain.base.configuration.KEY_SNAPSHOT_LEVELS_PER_PAGE
import net.postchain.base.configuration.KEY_SNAPSHOT_TO_KEEP
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv
import net.postchain.gtv.mapper.toObject

data class SnapshotBlockchainConfigurationData(
    @param:RawGtv
    val rawGtv: Gtv,

    @param:Name(KEY_SNAPSHOT_INTERVAL)
    @param:DefaultValue(defaultLong = 100)
    val snapshotInterval: Long,

    @param:Name(KEY_SNAPSHOT_LEVELS_PER_PAGE)
    @param:DefaultValue(defaultLong = 2)
    private val _levelsPerPage: Long,

    @param:Name(KEY_SNAPSHOT_TO_KEEP)
    @param:DefaultValue(defaultLong = 10)
    private val _snapshotsToKeep: Long
) {
    val levelsPerPage: Int
        get() = _levelsPerPage.toInt()

    val snapshotsToKeep: Int
        get() = _snapshotsToKeep.toInt()

    companion object : KLogging() {
        @JvmStatic
        val default = GtvFactory.gtv(mapOf()).toObject<SnapshotBlockchainConfigurationData>()
    }
}
