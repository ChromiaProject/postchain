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
        return getDatumWithType(ctx, height, contextId, datumId).first
    }

    fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): Pair<Gtv, Boolean> {
        val datum = getStateDatum(ctx, height, contextId, datumId)
        if (datum != null) {
            return datum to false
        }
        return getPermanentDatum(ctx, contextId, datumId) to true
    }

    fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val leafStoreState = dba.getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumId)

        if (leafStoreState != null) return GtvDecoder.decodeGtv(leafStoreState.data)

        return null
    }

    // TODO replace Triple and "bySize"?
    fun getDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Triple<Long, Gtv, Boolean>> {
        val datums = mutableListOf<Triple<Long, Gtv, Boolean>>()
        var offset = datumIdFrom
        do {
            val (datum, permanent) = getDatumWithType(ctx, height, contextId, offset) ?: break
            datums.add(Triple(offset, datum, permanent))
            offset++
        } while (datums.sumOf { it.second.nrOfBytes() } < maxDataSize)
        return datums
    }

    // TODO might not be needed
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

    // TODO might not be needed
    fun getPermanentDatumsBySize(ctx: EContext, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatumsBySize(ctx, datumIdFrom, maxDataSize)
    }
}
