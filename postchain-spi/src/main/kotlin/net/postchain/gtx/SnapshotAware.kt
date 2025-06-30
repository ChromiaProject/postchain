package net.postchain.gtx

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

const val SNAPSHOT_TABLE_PREFIX = "sys.x.gtx_module"

interface SnapshotAware {
    fun initializeSnapshotContext(context: SnapshotContext)

    fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv
}
