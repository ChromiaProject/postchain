package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkleHash
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.gtx.SnapshotAware

/**
 * TODO: We need to think about where and how to instantiate this repository
 */
class BaseSnapshotDatumRepository(
        private val snapshotModules: List<SnapshotAware>,
        private val levelsPerPage: Int,
        cryptoSystem: CryptoSystem,
        private val merkleHashCalculator: GtvMerkleHashCalculatorBase
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

    override fun getDatums(ctx: EContext, height: Long, contextId: Long, permanent: Boolean,
                           datumIdFrom: Long, datumIdTo: Long,
                           maxDataSize: Long, maxTime: Long): Pair<List<SnapshotDatum>, List<Pair<Long, Long>>> {
        val start = System.currentTimeMillis()
        val dba = DatabaseAccess.of(ctx)
        val module = dba.getSnapshotAwareModuleByContext(ctx, contextId)
        val data = mutableListOf<SnapshotDatum>()
        var size = 0L
        var offset = datumIdFrom
        val rangeGaps = mutableListOf<Pair<Long, Long>>()
        val addDatum: (SnapshotDatum?) -> Unit = {
            if (it != null) {
                data.lastOrNull()?.let { last ->
                    if (last.id + 1 != it.id) {
                        val rangeGap = Pair(last.id + 1, it.id - 1)
                        rangeGaps.add(rangeGap)
                        size += (rangeGap.second - rangeGap.second) * 50 // TODO what is the overhead for a 32 byte hash?
                    }
                }
                data.add(it)
                size += it.data.nrOfBytes()
                offset++
            }
        }
        val readMore: (Any?) -> Boolean = {
            it != null &&
                    System.currentTimeMillis() - start < maxTime &&
                    size < maxDataSize &&
                    offset <= datumIdTo
        }
        var lastDatum: SnapshotDatum? = null
        do {
            if (permanent) {
                module.getPermanentDatums(ctx, offset) {
                    if (it != null) {
                        addDatum(it)
                    }
                    lastDatum = it
                    readMore(it)
                }
            } else {
                dba.getStates(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, offset) {
                    if (it != null) {
                        addDatum(SnapshotDatum(it.stateN, GtvDecoder.decodeGtv(it.data), false))
                    }
                    readMore(it)
                }
            }
        } while (readMore(lastDatum))
        return data to rangeGaps
    }

    override fun getDatumHashes(ctx: EContext, height: Long, contextId: Long, permanent: Boolean, ranges: List<Pair<Long, Long>>): List<Pair<Long, Hash>> {
        return ranges.flatMap { range ->
            getDatums(ctx, height, contextId, permanent, range.first, range.second, Long.MAX_VALUE, Long.MAX_VALUE)
                    .first
                    .map { it.id to it.data.merkleHash(merkleHashCalculator) }
        }
    }

    override fun getRangeProof(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, datumIdTo: Long): RangeProof {
        val snapshotPageStore = SnapshotPageStore(ctx, levelsPerPage, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId")
        return snapshotPageStore.getRangeMerkleProof(height, datumIdFrom, datumIdTo)
    }

    private fun getPermanentDatum(ctx: EContext, contextId: Long, datumId: Long): Gtv? {
        val dba = DatabaseAccess.of(ctx)
        val module = dba.getSnapshotAwareModuleByContext(ctx, contextId)
        var datum: SnapshotDatum? = null
        module.getPermanentDatums(ctx, datumId) {
            datum = it
            false
        }
        return if (datum?.id == datumId) datum?.data else null
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

