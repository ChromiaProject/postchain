package net.postchain.gtx

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

const val SNAPSHOT_TABLE_PREFIX = "sys.x.gtx_module"

interface SnapshotAware {
    fun initializeSnapshotContext(context: SnapshotContext)

    // TODO: Maybe not needed
    /** Returns the maximum datum ID of the permanent data  */
    fun getPermanentDatumIdMax(ctx: EContext): Long?

    fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv

    fun getPermanentDatumsBySize(ctx: EContext, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>>

    /** Let the module rebuild its table data from snapshot datum data  */
    fun constructDatum(ctx: EContext, datumId: Long, datum: Gtv, isPermanent: Boolean)
}
