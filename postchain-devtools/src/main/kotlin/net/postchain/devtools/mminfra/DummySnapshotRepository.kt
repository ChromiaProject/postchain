package net.postchain.devtools.mminfra

import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull

class DummySnapshotRepository : SnapshotDatumRepository {
    override fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? = null

    override fun getStateDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? = null

    override fun getPermanentDatumIdMax(ctx: EContext, contextId: Long): Long? = null

    override fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv = GtvNull

    override fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): Pair<Gtv, Boolean> = GtvNull to false

    override fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? = null

    override fun getDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Triple<Long, Gtv, Boolean>> {
        TODO("Not yet implemented")
    }

    override fun getStateDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        TODO("Not yet implemented")
    }

    override fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv {
        TODO("Not yet implemented")
    }

    override fun getPermanentDatumsBySize(ctx: EContext, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        TODO("Not yet implemented")
    }

    override fun getLatestSnapshotHeight(ctx: EContext): Long? = null
}
