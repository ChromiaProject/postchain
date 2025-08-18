package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.gtx.SnapshotAware
import java.security.MessageDigest

/**
 * TODO: We need to think about where and how to instantiate this repository
 */
class BaseSnapshotDatumRepository(
        private val snapshotModules: List<SnapshotAware>
) : SnapshotDatumRepository {
    private val digestSystem = SimpleDigestSystem(MessageDigest.getInstance("SHA-256"))

    override fun getDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? {
        val stateMax = getStateDatumIdMax(ctx, height, contextId)
        val permanentMax = getPermanentDatumIdMax(ctx, contextId)
        return listOfNotNull(stateMax, permanentMax).maxOrNull()
    }

    override fun getContextMaxIds(ctx: EContext, height: Long): Map<Long, Long?> {
        val dba = DatabaseAccess.of(ctx)
        return dba.getSnapshotModuleContextIds(ctx).associateWith { contextId ->
            val stateMax = getStateDatumIdMax(ctx, height, contextId)
            val permanentMax = getPermanentDatumIdMax(ctx, contextId)
            listOfNotNull(stateMax, permanentMax).maxOrNull()
        }
    }

    override fun getStateDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        return dba.getStateNMax(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height)
    }

    override fun getPermanentDatumIdMax(ctx: EContext, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatumIdMax(ctx)
    }

    override fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv {
        return getDatumWithType(ctx, height, contextId, datumId).first
    }

    override fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): Pair<Gtv, Boolean> {
        val datum = getStateDatum(ctx, height, contextId, datumId)
        if (datum != null) {
            return datum to false
        }
        return getPermanentDatum(ctx, contextId, datumId) to true
    }

    override fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val leafStoreState = dba.getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumId)

        if (leafStoreState != null) return GtvDecoder.decodeGtv(leafStoreState.data)

        return null
    }

    // TODO replace Triple and "bySize"?
    override fun getDatumsBySize(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Triple<Long, Gtv, Boolean>> {
        val datums = mutableListOf<Triple<Long, Gtv, Boolean>>()
        var offset = datumIdFrom
        do {
            try {
                val (datum, permanent) = getDatumWithType(ctx, height, contextId, offset) ?: break
                datums.add(Triple(offset, datum, permanent))
            } catch (e: Exception) {
                // TODO update when decided on how to "identify the end" of available datums
                break
            }
            offset++
        } while (datums.sumOf { it.second.nrOfBytes() } < maxDataSize)
        return datums
    }

    override fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatum(ctx, datumId) // We assume the module will throw if it can't resolve a datum with this ID
    }

    // TODO might not be needed
    override fun getPermanentDatumsBySize(ctx: EContext, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<Pair<Long, Gtv>> {
        val dba = DatabaseAccess.of(ctx)
        val moduleName = dba.getSnapshotContextModule(ctx, contextId)

        val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                ?: TODO("We need to think more about this scenario, could be a module that is no longer used?")

        return module.getPermanentDatumsBySize(ctx, datumIdFrom, maxDataSize)
    }

    override fun getLatestSnapshotHeight(ctx: EContext): Long? {
        val rootSnapshotStore = SnapshotPageStore(ctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")

        return rootSnapshotStore.getLastSnapshotHeight()
    }
}
