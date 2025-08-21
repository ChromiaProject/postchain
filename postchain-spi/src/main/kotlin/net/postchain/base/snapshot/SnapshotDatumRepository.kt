package net.postchain.base.snapshot

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface SnapshotDatumRepository {
    fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long?

    fun getStateDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long?

    fun getPermanentDatumIdMax(ctx: EContext, contextId: Long): Long?

    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv

    fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): Pair<Gtv, Boolean>

    fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv?

    // TODO replace Triple and "bySize"?
    fun getDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Triple<Long, Gtv, Boolean>>

    // TODO might not be needed
    fun getStateDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>>

    fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv

    // TODO might not be needed
    fun getPermanentDatumsBySize(ctx: EContext, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>>

    fun getLatestSnapshotHeight(ctx: EContext): Long?
}
