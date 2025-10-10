package net.postchain.gtx

import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.core.EContext

const val SNAPSHOT_TABLE_PREFIX = "sys.x.gtx_module"

/**
 * This interface should be implemented by all modules that want to be snapshot compatible. More information
 * can be found in the [documentation](../../../../../../../doc/snapshots/snapshot-modules.md).
 */
interface SnapshotAware {
    /** Called at startup to provide the module with the [SnapshotContext] */
    fun initializeSnapshotContext(context: SnapshotContext)

    /** Provides the initial state of datums. These will be added once when the module snapshot context is created for
     *  the module. */
    fun getInitialDatums(ctx: EContext): List<SnapshotDatum> = emptyList()

    /** Returns the maximum datum ID of this modules permanent data, or null if no data exists  */
    fun getPermanentDatumIdMax(ctx: EContext): Long?

    /** Read and call `datumHandler` for each permanent datum in sequence from provided start `datumIdFrom`.
     * @param datumIdFrom The ID of the first datum to read.
     * @param datumHandler The handler to call for each datum. If `datum` is null the end is reached. Modules continues
     * to read datums until this returns false.
     */
    fun getPermanentDatums(ctx: EContext, datumIdFrom: Long, datumHandler: (datum: SnapshotDatum?) -> Boolean)

    /** Called before the start of the snapshot import process */
    fun initializeImport(ctx: EContext) {}

    /** Let the module rebuild its table data from snapshot datum data  */
    fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>)

    /** Called after the snapshot import process */
    fun finalizeImport(ctx: EContext) {}
}
