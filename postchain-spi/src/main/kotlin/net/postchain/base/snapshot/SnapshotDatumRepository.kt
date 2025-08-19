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

    fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? {
        val stateMax = getStateDatumIdMax(ctx, height, contextId)
        val permanentMax = getPermanentDatumIdMax(ctx, contextId)
        return listOfNotNull(stateMax, permanentMax).maxOrNull()
    }

    fun getStateDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        return dba.getStateNMax(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height)
    }

    fun getPermanentDatumIdMax(ctx: EContext, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatumIdMax(ctx)
    }

    fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv {
        return getStateDatum(ctx, height, contextId, datumId) ?: getPermanentDatum(ctx, contextId, datumId)
    }

    fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val leafStoreState = dba.getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumId)

        if (leafStoreState != null) return GtvDecoder.decodeGtv(leafStoreState.data)

        return null
    }

    fun getStateDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        val dba = DatabaseAccess.of(ctx)
        return dba.getStatesBySize(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumIdFrom, maxDataSize)
                .map { it.stateN to GtvDecoder.decodeGtv(it.data) }
    }

    fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatum(ctx, datumId) // We assume the module will throw if it can't resolve a datum with this ID
    }

    fun getPermanentDatumsBySize(ctx: EContext, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatumsBySize(ctx, datumIdFrom, maxDataSize)
    }
}
