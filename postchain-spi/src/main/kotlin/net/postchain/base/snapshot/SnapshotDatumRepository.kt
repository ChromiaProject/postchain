package net.postchain.base.snapshot

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface SnapshotDatumRepository {
    fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long?

    fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?>

    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv?

    fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatum?

    // TODO: remove maxDataSize and use MAX_PACKAGE_CONTENT_BYTES as hard limit instead?
    fun getDatums(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long, maxTime: Long): List<SnapshotDatum>

    fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof

    fun getLatestSnapshotHeight(ctx: EContext): Long?
}

data class SnapshotDatum(val id: Long, val data: Gtv, val isPermanent: Boolean)

