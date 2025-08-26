package net.postchain.devtools.mminfra

import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.SnapshotDatumData
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull

class DummySnapshotRepository : SnapshotDatumRepository {
    override fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? = null

    override fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv = GtvNull

    override fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatumData? = null

    override fun getDatums(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<SnapshotDatum> = emptyList()

    override fun getLatestSnapshotHeight(ctx: EContext): Long? = null

    override fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?> = emptyMap()
}
