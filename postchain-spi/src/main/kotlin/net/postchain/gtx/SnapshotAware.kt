package net.postchain.gtx

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

const val SNAPSHOT_TABLE_PREFIX = "sys.x.gtx_module"

interface SnapshotAware {
    fun initializeSnapshotContext(context: SnapshotContext)

    /** Returns the maximum datum ID of the permanent data, or null if no data is available  */
    fun getPermanentDatumIdMax(ctx: EContext): Long?

    fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv?

    /** Let the module rebuild its table data from snapshot datum data  */
    fun constructDatum(ctx: EContext, datumId: Long, datum: Gtv, isPermanent: Boolean)
}
