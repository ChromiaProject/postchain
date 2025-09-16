package net.postchain.base.snapshot

import net.postchain.core.EContext
import net.postchain.gtv.Gtv

interface SnapshotDatumRepository {

    /** Get latest and highest datum ID in use for a context */
    fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long?

    /** Get latest and highest datum ID in use for all contexts
     *  @return Map<ContextId, MaxId> */
    fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?>

    /** Get a single dynamic or permanent datum */
    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv?

    /** Get a single datum returned with type information */
    fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatum?

    /** Get as many datums as possible until either size or time limit is reached */
    fun getDatums(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long, maxTime: Long): List<SnapshotDatum>

    /** Get a proof for a range of datums that can be verified against context root hash */
    fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof

    /** Return the height of the latest snapshot */
    fun getLatestSnapshotHeight(ctx: EContext): Long?
}

data class SnapshotDatum(val id: Long, val data: Gtv, val isPermanent: Boolean)

