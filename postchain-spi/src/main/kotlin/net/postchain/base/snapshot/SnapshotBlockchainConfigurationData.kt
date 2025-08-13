package net.postchain.base.snapshot

import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.DefaultValue
import net.postchain.gtv.mapper.Name
import net.postchain.gtv.mapper.RawGtv

data class SnapshotBlockchainConfigurationData(
    @RawGtv
    val rawGtv: Gtv,
    @Name("interval")
    @DefaultValue(defaultLong = 100) // TODO: What is reasonable?
    val snapshotInterval: Long
)
