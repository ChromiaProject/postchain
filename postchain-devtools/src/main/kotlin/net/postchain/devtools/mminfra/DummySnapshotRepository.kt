package net.postchain.devtools.mminfra

import net.postchain.base.snapshot.RangeProof
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.common.data.Hash
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull

class DummySnapshotRepository : SnapshotDatumRepository {
    override fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? = null

    override fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv = GtvNull

    override fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatum? = null

    override fun getDatums(ctx: EContext, height: Long, contextId: Long, permanent: Boolean, datumIdFrom: Long, datumIdTo: Long, maxDataSize: Long, maxTime: Long): Pair<List<SnapshotDatum>, List<Pair<Long, Long>>> = emptyList<SnapshotDatum>() to emptyList()

    override fun getDatumHashes(ctx: EContext, height: Long, contextId: Long, permanent: Boolean, ranges: List<Pair<Long, Long>>): List<Pair<Long, Hash>> {
        TODO("Not yet implemented")
    }

    override fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof {
        TODO("Not yet implemented")
    }

    override fun getLatestSnapshotHeight(ctx: EContext): Long? = null

    override fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?> = emptyMap()
}
