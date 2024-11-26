package net.postchain.base.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TreeMap

class PruneSnapshotsTest : SnapshotBaseIT() {

    override val protocolVersion: Int = 2
    private val snapshotPageTable = "${PREFIX}_snapshot"

    @OptIn(ExperimentalStdlibApi::class)
    private fun state(nonce: Byte) = nonce.toHexString().padEnd(64, 'f').hexStringToByteArray()

    @Test
    fun `prune snapshots when snapshotsToKeep is 0`() {
        runSnapshotCommand(appConfig, snapshotsToKeep = 0) { db, ctx, snapshot ->
            // Init DB
            initBlockchain(0L, ctx)

            // Height 0: Insert state, update snapshot and verify page and state
            val height0 = 0L
            val states = TreeMap<Long, Hash>()
            val account0 = 0L
            states[account0] = state(0)

            // Verify
            db.insertState(ctx, PREFIX, height0, account0, states[account0]!!)
            snapshot.updateSnapshot(height0, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())
            // Try to pruneSnapshot(), and verify page and state
            snapshot.pruneSnapshot(height0)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())

            // Height 1: Update state, update snapshot and verify page and state
            val height1 = 1L
            states[account0] = state(1)
            db.insertState(ctx, PREFIX, height1, account0, states[account0]!!)
            snapshot.updateSnapshot(height1, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
            ), setOf())
            // Try to pruneSnapshot(), and verify page and state
            snapshot.pruneSnapshot(height1)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
            ), setOf())

            // Height 2: Update state, update snapshot and verify page and state
            val height2 = 2L
            states[account0] = state(2)
            db.insertState(ctx, PREFIX, height2, account0, states[account0]!!)
            snapshot.updateSnapshot(height2, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
                    height2 to state(2),
            ), setOf())
            // Try to pruneSnapshot(), and verify page and state
            snapshot.pruneSnapshot(height2)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
                    height2 to state(2),
            ), setOf())

            // Height 3: Update state, update snapshot and verify page and state
            val height3 = 2L
            states[account0] = state(3)
            db.insertState(ctx, PREFIX, height3, account0, states[account0]!!)
            snapshot.updateSnapshot(height3, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
                    height2 to state(2),
                    height3 to state(3),
            ), setOf())
            // Try to pruneSnapshot(), and verify page and state
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
                    height2 to state(2),
                    height3 to state(3),
            ), setOf())
        }
    }

    @Test
    fun `prune snapshots when snapshotsToKeep is 1`() {
        /**
         * Multiple runSnapshotCommand { } blocks are needed for syncing with DB state when debugging.
         */
        runSnapshotCommand(appConfig) { _, ctx, _ ->
            initBlockchain(0L, ctx)
        }

        // Height 0: Insert state, update snapshot and verify page and state
        val height0 = 0L
        val states = TreeMap<Long, Hash>()
        val account0 = 0L
        states[account0] = state(0)
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height0, account0, states[account0]!!)
            snapshot.updateSnapshot(height0, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())
        }
        // Prune snapshots, then verify page and state
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height0)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())
        }

        // Height 1: Update state, update snapshot and verify page and state
        val height1 = 1L
        states[account0] = state(1)
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height1, account0, states[account0]!!)
            snapshot.updateSnapshot(height1, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
            ), setOf())
        }
        // Prune snapshots, then verify page and state
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height1)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height1 to state(1),
            ), setOf(
                    height0
            ))
        }

        // Height 2: Update state, update snapshot and verify page and state
        val height2 = 2L
        states[account0] = state(2)
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height2, account0, states[account0]!!)
            snapshot.updateSnapshot(height2, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height1 to state(1),
                    height2 to state(2),
            ), setOf(
                    height0
            ))
            snapshot.pruneSnapshot(height2)
        }
        // Prune snapshots
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { _, _, snapshot -> snapshot.pruneSnapshot(height2) }
        // Verify page and state
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, _ ->
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height2 to state(2),
            ), setOf(
                    height0,
                    height1
            ))
        }

        // Height 3: Update state, update snapshot and verify page and state
        val height3 = 3L
        states[account0] = state(3)
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height3, account0, states[account0]!!)
            snapshot.updateSnapshot(height3, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height2 to state(2),
                    height3 to state(3),
            ), setOf(
                    height0,
                    height1
            ))
        }
        // Prune snapshots
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { _, _, snapshot -> snapshot.pruneSnapshot(height3) }
        // Verify page and state
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height3 to state(3),
            ), setOf(
                    height0,
                    height1,
                    height2
            ))
        }

        // Prune snapshots at height 100 and 101 without updating states
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { _, _, snapshot -> snapshot.pruneSnapshot(100L) }
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { _, _, snapshot -> snapshot.pruneSnapshot(101L) }
        // Verify page and state
        runSnapshotCommand(appConfig, snapshotsToKeep = 1) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height3 to state(3),
            ), setOf(
                    height0,
                    height1,
                    height2
            ))
        }
    }

    @Test
    fun `prune snapshots when snapshotsToKeep is 2 (default value)`() {
        runSnapshotCommand(appConfig) { _, ctx, _ ->
            initBlockchain(0L, ctx)
        }

        // Height 0: Insert state, update snapshot and verify page and state
        val height0 = 0L
        val states = TreeMap<Long, Hash>()
        val account0 = 0L
        states[account0] = state(0)
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height0, account0, states[account0]!!)
            snapshot.updateSnapshot(height0, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())
        }
        // Prune snapshots, then verify page and state
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height0)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
            ), setOf())
        }

        // Height 1: Update state, update snapshot and verify page and state
        val height1 = 1L
        states[account0] = state(1)
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height1, account0, states[account0]!!)
            snapshot.updateSnapshot(height1, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
            ), setOf())
        }
        // Prune snapshots, then verify page and state
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height1)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
            ), setOf())
        }

        // Height 2: Update state, update snapshot and verify page and state
        val height2 = 2L
        states[account0] = state(2)
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height2, account0, states[account0]!!)
            snapshot.updateSnapshot(height2, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height0 to state(0),
                    height1 to state(1),
                    height2 to state(2),
            ), setOf())
            snapshot.pruneSnapshot(height2)
        }
        // Prune snapshots
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(height2) }
        // Verify page and state
        runSnapshotCommand(appConfig) { db, ctx, _ ->
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height1 to state(1),
                    height2 to state(2),
            ), setOf(
                    height0
            ))
        }

        // Height 3: Update state, update snapshot and verify page and state
        val height3 = 3L
        states[account0] = state(3)
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            db.insertState(ctx, PREFIX, height3, account0, states[account0]!!)
            snapshot.updateSnapshot(height3, states, protocolVersion).toHex()
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height1 to state(1),
                    height2 to state(2),
                    height3 to state(3),
            ), setOf(
                    height0
            ))
        }
        // Prune snapshots
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(height3) }
        // Verify page and state
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height2 to state(2),
                    height3 to state(3),
            ), setOf(
                    height0,
                    height1,
            ))
        }

        // Prune snapshots at height 100 and 101 without updating states
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(100L) }
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(101L) }
        // Verify page and state
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    height2 to state(2),
                    height3 to state(3),
            ), setOf(
                    height0,
                    height1,
            ))
        }
    }

    @Test
    fun `prune snapshots after multiple updates when snapshotsToKeep is 2`() {
        val account0 = 0L

        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            // Init DB
            initBlockchain(0L, ctx)

            // Height 0: Insert state, update snapshot and verify page and state
            val states = TreeMap<Long, Hash>()

            // Blocks: 0, 1, 2, 3
            for (height in 0L..3L) {
                // Height i: Update state, update snapshot and verify page and state
                states[account0] = state(height.toByte())
                db.insertState(ctx, PREFIX, height, account0, states[account0]!!)
                snapshot.updateSnapshot(height, states, protocolVersion).toHex()
            }

            // Verify page and state
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    0L to state(0),
                    1L to state(1),
                    2L to state(2),
                    3L to state(3),
            ), setOf())
        }

        // Prune snapshots
        val height3 = 3L
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(height3) }

        // Verify page and state after pruning
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            snapshot.pruneSnapshot(height3)
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    2L to state(2),
                    3L to state(3),
            ), setOf(
                    0L,
                    1L,
            ))
        }
    }

    @Test
    fun `prune snapshots for two accounts in two different pages after multiple updates when snapshotsToKeep is 2`() {
        val account0 = 0L // page0
        val account4 = 4L // page1

        /**
         * Multiple runSnapshotCommand { } blocks are needed for syncing with DB state when debugging.
         */
        runSnapshotCommand(appConfig) { _, ctx, _ ->
            initBlockchain(0L, ctx)
        }

        // Insert state, and update snapshot
        //  - Height 0, account0
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height0 = 0L
            val states0 = TreeMap<Long, Hash>()
            states0[account0] = state(height0.toByte())
            db.insertState(ctx, PREFIX, height0, account0, states0[account0]!!)
            snapshot.updateSnapshot(height0, states0, protocolVersion).toHex()
        }

        //  - Height 1, account0 and account4
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height1 = 1L
            val states1 = TreeMap<Long, Hash>()
            states1[account0] = state(height1.toByte())
            states1[account4] = state(0x41)
            db.insertState(ctx, PREFIX, height1, account0, states1[account0]!!)
            db.insertState(ctx, PREFIX, height1, account4, states1[account4]!!)
            snapshot.updateSnapshot(height1, states1, protocolVersion).toHex()
        }

        //  - Height 2, account4
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height2 = 2L
            val states2 = TreeMap<Long, Hash>()
            states2[account4] = state((0x42).toByte())
            db.insertState(ctx, PREFIX, height2, account4, states2[account4]!!)
            snapshot.updateSnapshot(height2, states2, protocolVersion).toHex()
        }

        //  - Height 3, account4
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height3 = 3L
            val states3 = TreeMap<Long, Hash>()
            states3[account4] = state(0x43)
            db.insertState(ctx, PREFIX, height3, account4, states3[account4]!!)
            snapshot.updateSnapshot(height3, states3, protocolVersion).toHex()
        }

        // Verify pages and state
        runSnapshotCommand(appConfig) { db, ctx, _ ->
            //  - Level 0
            // - account0
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    0L to state(0),
                    1L to state(1),
            ), setOf())
            // - account4
            verifyLevel0PagesAndAccountStates(ctx, db, account4, 4, mapOf(
                    1L to state(0x41),
                    2L to state(0x42),
                    3L to state(0x43),
            ), setOf())

            //  - Level 2
            assertPageIsNotNull(ctx, db, 1L, 2, 0)
            assertPageIsNotNull(ctx, db, 2L, 2, 0)
            assertPageIsNotNull(ctx, db, 3L, 2, 0)
        }

        // Prune snapshots at height 3
        val height3 = 3L
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(height3) }

        // Verify pages and states
        fun verifyPagesAndStatesAfterHeight3() {
            runSnapshotCommand(appConfig) { db, ctx, _ ->
                //  - Level 0
                // - account0
                verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                        0L to state(0),
                        1L to state(1),
                ), setOf())
                // - account4
                verifyLevel0PagesAndAccountStates(ctx, db, account4, 4, mapOf(
                        2L to state(0x42),
                        3L to state(0x43),
                ), setOf(
                        1L,
                ))

                //  - Level 2
                assertPageIsNull(ctx, db, 1L, 2, 0) // page (1L, 2, 0) is removed
                assertPageIsNotNull(ctx, db, 2L, 2, 0)
                assertPageIsNotNull(ctx, db, 3L, 2, 0)
            }
        }

        verifyPagesAndStatesAfterHeight3()

        //  - Height 4, 5: no state updates
        runSnapshotCommand(appConfig) { _, _, snapshot ->
            snapshot.updateSnapshot(4L, TreeMap(), protocolVersion).toHex()
            snapshot.updateSnapshot(5L, TreeMap(), protocolVersion).toHex()
        }
        verifyPagesAndStatesAfterHeight3() // Make sure that the state is the same as after Height3

        //  - Height 6, account0 and account4
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height6 = 6L
            val states6 = TreeMap<Long, Hash>()
            states6[account0] = state(height6.toByte())
            states6[account4] = state(0x46)
            db.insertState(ctx, PREFIX, height6, account0, states6[account0]!!)
            db.insertState(ctx, PREFIX, height6, account4, states6[account4]!!)
            snapshot.updateSnapshot(height6, states6, protocolVersion).toHex()
        }

        // Verify pages and state
        runSnapshotCommand(appConfig) { db, ctx, _ ->
            //  - Level 0
            // - account0
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    0L to state(0),
                    1L to state(1),
                    6L to state(6),
            ), setOf())
            // - account4
            verifyLevel0PagesAndAccountStates(ctx, db, account4, 4, mapOf(
                    2L to state(0x42),
                    3L to state(0x43),
                    6L to state(0x46),
            ), setOf(
                    1L, // removed state
            ))

            //  - Level 2
            assertPageIsNotNull(ctx, db, 6L, 2, 0)
        }

        // Prune snapshots at height 6
        runSnapshotCommand(appConfig) { _, _, snapshot -> snapshot.pruneSnapshot(6L) }

        // Verify pages and state
        runSnapshotCommand(appConfig) { db, ctx, _ ->
            //  - Level 0
            // - account0
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    // 0L's state can't be deleted at height 6, since there is no page (_, 0, 0)
                    // at heights below `lowestSnapshotHeightToKeep`. It will be deleted later
                    0L to state(0),
                    1L to state(1),
                    6L to state(6),
            ), setOf())
            // - account4
            verifyLevel0PagesAndAccountStates(ctx, db, account4, 4, mapOf(
                    3L to state(0x43),
                    6L to state(0x46),
            ), setOf(
                    1L,
                    // was removed at height 6, since there is a page (3, 0, 4) at height `lowestSnapshotHeightToKeep = 3`.
                    2L,
            ))

            //  - Level 2
            assertPageIsNotNull(ctx, db, 3L, 2, 0)
            assertPageIsNotNull(ctx, db, 6L, 2, 0)
        }

        //  - Height 7: account0
        runSnapshotCommand(appConfig) { db, ctx, snapshot ->
            val height7 = 7L
            val states7 = TreeMap<Long, Hash>()
            states7[account0] = state(height7.toByte())
            db.insertState(ctx, PREFIX, height7, account0, states7[account0]!!)
            snapshot.updateSnapshot(height7, states7, protocolVersion).toHex()
        }

        // Snapshot pruning
        runSnapshotCommand(appConfig) { _, _, snapshot ->
            snapshot.pruneSnapshot(7L)
        }

        // Verify pages and state
        runSnapshotCommand(appConfig) { db, ctx, _ ->
            //  - Level 0
            // - account0
            verifyLevel0PagesAndAccountStates(ctx, db, account0, 0, mapOf(
                    6L to state(6),
            ), setOf(
                    // Pages (0, 0, 0) and (1, 0, 0) were removed at height 7, b/c
                    // there is a page (6, 0, 0) at height 6 at height `lowestSnapshotHeightToKeep = 6`.
                    0L,
                    1L,
            ))
            // - account4
            verifyLevel0PagesAndAccountStates(ctx, db, account4, 4, mapOf(
                    6L to state(0x46),
            ), setOf(
                    0L,
                    1L,
                    2L,
                    // Pages (3, 0, 4) and (3, 2, 0) were removed at height 7,
                    // since there are pages (6, 0, 4) and (6, 2, 0) at height `lowestSnapshotHeightToKeep = 6`.
                    3L,
            ))

            //  - Level 2
            assertPageIsNull(ctx, db, 3L, 2, 0)
        }
    }

    private fun runSnapshotCommand(
            appConfig: AppConfig,
            snapshotsToKeep: Int = 2,
            chainId: Long = 0L,
            op: (db: DatabaseAccess, ctx: EContext, snapshot: SnapshotPageStore) -> Unit
    ) {
        runStorageCommand(appConfig, chainId) { ctx ->
            op(
                    DatabaseAccess.of(ctx),
                    ctx,
                    SnapshotPageStore(ctx, levelsPerPage, snapshotsToKeep, ds, PREFIX)
            )
        }
    }

    private fun verifyLevel0PagesAndAccountStates(
            ctx: EContext,
            db: DatabaseAccess,
            account: Long,
            left: Long,
            existingStates: Map<Long, Hash>, // height -> state
            nonExistingState: Set<Long> // height
    ) {
        // Verify existing pages and states
        existingStates.forEach { (height, expectedState) ->
            // verify page
            assertPageIsNotNull(ctx, db, height, 0, left)
            // verify account state
            val state = db.getAccountState(ctx, PREFIX, height, account)
            assertThat(state?.blockHeight).isEqualTo(height)
            assertThat(state?.data?.toHex()).isEqualTo(expectedState.toHex())
        }

        // Verify non-existing pages and states
        nonExistingState.forEach { height ->
            // verify page
            assertPageIsNull(ctx, db, height, 0, left)
            // verify account state
            val state = db.getAccountState(ctx, PREFIX, height, account)
            assertTrue(state == null || state.blockHeight < height)
        }
    }

    private fun assertPageIsNotNull(
            ctx: EContext,
            db: DatabaseAccess,
            height: Long,
            level: Int,
            left: Long,
    ) {
        val page = db.getPageAtHeight(ctx, snapshotPageTable, height, level, left)
        assertThat(page).isNotNull()
        assertThat(page?.blockHeight).isEqualTo(height)
    }

    private fun assertPageIsNull(
            ctx: EContext,
            db: DatabaseAccess,
            height: Long,
            level: Int,
            left: Long,
    ) {
        assertThat(
                db.getPageAtHeight(ctx, snapshotPageTable, height, level, left)
        ).isNull()
    }
}