package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.gtx.SnapshotAware

/**
 * TODO: We need to think about where and how to instantiate this repository
 */
class BaseSnapshotDatumRepository(
        private val snapshotModules: List<SnapshotAware>,
        private val levelsPerPage: Int,
        cryptoSystem: CryptoSystem
) : SnapshotDatumRepository {
    private val digestSystem = SimpleDigestSystem(cryptoSystem)
    private val snapshotModuleByContextMap = mutableMapOf<Long, SnapshotAware>()

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

    private fun getStateDatumIdMax(ctx: EContext, height: Long, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        return dba.getStateNMax(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height)
    }

    private fun getPermanentDatumIdMax(ctx: EContext, contextId: Long): Long? {
        val dba = DatabaseAccess.of(ctx)
        val module = dba.getSnapshotAwareModuleByContext(ctx, contextId)
        return module.getPermanentDatumIdMax(ctx)
    }

    override fun getDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? {
        return getDatumWithType(ctx, height, contextId, datumId)?.data
    }

    override fun getDatumWithType(ctx: EContext, height: Long, contextId: Long, datumId: Long): SnapshotDatum? {
        val datum = getStateDatum(ctx, height, contextId, datumId)
        if (datum != null) {
            return SnapshotDatum(datumId, datum, false)
        }
        val permanentDatum = getPermanentDatum(ctx, contextId, datumId)
        if (permanentDatum != null) {
            return SnapshotDatum(datumId, permanentDatum, true)
        }
        return null
    }

    private fun getStateDatum(ctx: EContext, height: Long, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val leafStoreState = dba.getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumId)

        if (leafStoreState != null) return GtvDecoder.decodeGtv(leafStoreState.data)

        return null
    }

    override fun getDatums(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long): List<SnapshotDatum> {
        val datums = mutableListOf<SnapshotDatum>()
        var size = 0
        var offset = datumIdFrom
        do {
            val datum = getDatumWithType(ctx, height, contextId, offset) ?: break
            datums.add(SnapshotDatum(offset, datum.data, datum.isPermanent))
            size += datum.data.nrOfBytes()
            offset++
        } while (size < maxDataSize)
        return datums
    }

    override fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof {
        val snapshotPageStore = SnapshotPageStore(ctx, levelsPerPage, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId")
        return snapshotPageStore.getRangeMerkleProof(height, datumIdFrom, datumIdTo)
    }

    private fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val module = dba.getSnapshotAwareModuleByContext(ctx, contextId)
        return module.getPermanentDatum(ctx, datumId) // We assume the module will throw if it can't resolve a datum with this ID
    }

    override fun getLatestSnapshotHeight(ctx: EContext): Long? {
        val rootSnapshotStore = SnapshotPageStore(ctx, levelsPerPage, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")

        return rootSnapshotStore.getLastSnapshotHeight()
    }

    private fun DatabaseAccess.getSnapshotAwareModuleByContext(ctx: EContext, contextId: Long): SnapshotAware {
        return snapshotModuleByContextMap[contextId] ?: let {
            val moduleName = getSnapshotContextModule(ctx, contextId)
            val module = snapshotModules.find { it::class.java.canonicalName == moduleName }
                    ?: throw ProgrammerMistake("No module found for snapshot context id $contextId")
            snapshotModuleByContextMap[contextId] = module
            module
        }
    }
}

