package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.gtx.SnapshotAware

/**
 * TODO: We need to think about where and how to instantiate this repository
 */
class SnapshotDatumRepository(
        private val snapshotModules: List<SnapshotAware>
) {

    /**
     * TODO: This assumes that datums can never switch from being permanent to non-permanent and vice versa
     * Probably makes sense but should be double checked
     */
    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv {
        val dba = DatabaseAccess.of(ctx)
        val leafStoreState = dba.getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumId)

        if (leafStoreState != null) return GtvDecoder.decodeGtv(leafStoreState.data)

        // No leaf state so it should be a permanent datum, we need to ask the GTX Module
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatum(ctx, datumId) // We assume the module will throw if it can't resolve a datum with this ID
    }
}
