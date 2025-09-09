package net.postchain.base.snapshot

import net.postchain.common.data.Hash
import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface SnapshotDatumRepository {
    fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long?

    fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?>

    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv?

    fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatum?

    fun getDatums(ctx: EContext, height: Long, contextId: Long, permanent: Boolean, datumIdFrom: Long, datumIdTo: Long, maxDataSize: Long, maxTime: Long): Pair<List<SnapshotDatum>, List<Pair<Long, Long>>>

    fun getDatumHashes(ctx: EContext, height: Long, contextId: Long, permanent: Boolean, ranges: List<Pair<Long, Long>>): List<Pair<Long, Hash>>

    fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof

    fun getLatestSnapshotHeight(ctx: EContext): Long?
}

data class SnapshotDatum(val id: Long, val data: Gtv, val isPermanent: Boolean)

