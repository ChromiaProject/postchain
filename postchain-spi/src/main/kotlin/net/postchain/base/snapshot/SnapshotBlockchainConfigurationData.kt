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
    @RawGtv
    val rawGtv: Gtv,

    @Name(KEY_SNAPSHOT_INTERVAL)
    @DefaultValue(defaultLong = 100) // TODO: What is reasonable?
    val snapshotInterval: Long,

    @Name(KEY_SNAPSHOT_LEVELS_PER_PAGE)
    @DefaultValue(defaultLong = 2)
    private val _levelsPerPage: Long,

    @Name(KEY_SNAPSHOT_TO_KEEP)
    @DefaultValue(defaultLong = 10)
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
