package net.postchain.base.snapshot

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatabaseAccess.StateData
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.gtx.SnapshotAware
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

    override fun getDatums(ctx: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long, maxTime: Long): List<SnapshotDatum> {
        val start = System.currentTimeMillis()
        val data = mutableListOf<SnapshotDatum>()
        var size = 0
        var offset = datumIdFrom
        do {
            val datum = getDatumWithType(ctx, height, contextId, offset) ?: break
            data.add(SnapshotDatum(offset, datum.data, datum.isPermanent))
            size += datum.data.nrOfBytes()
            offset++
            if (System.currentTimeMillis() - start >= maxTime) {
                break
            }
        } while (size < maxDataSize)
        return data
    }

    class DataSource<T>(
            var active: Boolean = true,
            val queue: BlockingQueue<T> = LinkedBlockingQueue(10),
            var dataAvailable: Boolean = true,
            val snapshotDatumMapper: (T) -> SnapshotDatum
    ) {
        private var currentValue: SnapshotDatum? = null

        private fun getCurrentValue(): SnapshotDatum? {
            if (currentValue == null) {
                val value = queue.poll(5, TimeUnit.MILLISECONDS)
                if (value != null) {
                    currentValue = snapshotDatumMapper(value)
                }
            }
            return currentValue
        }

        fun getSnapshotDatumIfId(datumId: Long): SnapshotDatum? {
            val value = getCurrentValue()
            if (value != null && value.id == datumId) {
                currentValue = null
                return value
            }
            return null
        }

        fun hasMoreData(): Boolean = active && (queue.isNotEmpty() || dataAvailable)
    }

    private fun <T> threadFunction(
            ctx: EContext,
            dataSource: DataSource<T>,
            datumProvider: (ctx: EContext, op: (stateData: T?) -> Boolean) -> Unit
    ): Thread {
        return Thread.startVirtualThread {
            datumProvider(ctx) {
                if (it == null) {
                    dataSource.dataAvailable = false
                    false
                } else {
                    while (dataSource.active && !dataSource.queue.offer(it, 5, TimeUnit.MILLISECONDS) && dataSource.dataAvailable) {
                    }
                    dataSource.active
                }
            }
        }
    }

    override fun getDatumsFaster(ctx1: EContext, ctx2: EContext, height: Long, contextId: Long, datumIdFrom: Long, maxDataSize: Long, maxTime: Long): List<SnapshotDatum> {
        val start = System.currentTimeMillis()
        val dynamicSource = DataSource<StateData> {
            SnapshotDatum(it.stateN, GtvDecoder.decodeGtv(it.data), false)
        }
        val permanentSource = DataSource<SnapshotDatum> {
            SnapshotDatum(it.id, it.data, true)
        }

        val threads = listOf(
                threadFunction(ctx1, dynamicSource) { ctx, op ->
                    DatabaseAccess.of(ctx).streamStates(ctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", height, datumIdFrom, op)
                },
                threadFunction(ctx1, permanentSource) { ctx, op ->
                    val module = DatabaseAccess.of(ctx).getSnapshotAwareModuleByContext(ctx, contextId)
                    module.streamPermanentDatums(ctx2, datumIdFrom, op)
                }
        )

        val data = mutableListOf<SnapshotDatum>()
        var size = 0
        var currentDatumId = datumIdFrom
        do {
            val snapshotDatum = dynamicSource.getSnapshotDatumIfId(currentDatumId) ?:
                permanentSource.getSnapshotDatumIfId(currentDatumId)

            if (snapshotDatum != null) {
                data.add(snapshotDatum)
                size += snapshotDatum.data.nrOfBytes()
                currentDatumId++
            }
        } while (
                System.currentTimeMillis() - start < maxTime &&
                size < maxDataSize &&
                (dynamicSource.hasMoreData() || permanentSource.hasMoreData()))

        dynamicSource.active = false
        permanentSource.active = false
        threads.forEach { it.join() }
        println(": Read / Loaded ${data.size} records in ${System.currentTimeMillis() - start} ms")
        return data
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

