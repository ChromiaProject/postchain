package net.postchain.integrationtest.sync

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.support.expected
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.data.SnapshotSyncContextState
import net.postchain.base.data.SnapshotSyncState
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.snapshot.SnapshotTestBase
import net.postchain.devtools.snapshot.hasSnapshotSyncEvent
import net.postchain.ebft.message.SnapshotBlockHeaderContextData
import net.postchain.ebft.syncmanager.common.SnapshotSyncEvent
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import net.postchain.integrationtest.snapshot.SnapshotTestModule
import org.apache.commons.dbutils.QueryRunner
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SnapshotSyncSlowIntegrationTest : SnapshotTestBase() {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun syncFromSnapshot() {
        startManagedSystem(4, 1)
        val restartNodeIdx = 4

        val initialConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(initialConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Load new config at height 5
        val newConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_updated_4.xml")!!.readText())
        addDappBlockchainConfiguration(c1, GtvEncoder.encodeGtv(newConfig), 5)
        buildBlockNoWait(nodes, c1, 4)
        val nodeSetups = getChainNodeSetups(c1)
        nodeSetups.forEach { awaitChainRunning(it.sequenceNumber.nodeNumber, c1, 4) }

        // Build some more blocks
        buildBlock(c1, 9)

        // Emit something here so we get some snapshot data
        buildInitialDatumBlocks(datumLength = 20, moduleDatums = mapOf(
                "a" to 3L,
                "b" to 3L,
        ))

        // Assert that we could snapshot sync the chain on the replica node
        restartAndAwaitSnapshotSync(4, 10)

        assertThat(nodes).hasSameSnapshotRootHash(10)
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, 10, newConfig)

        // Build enough blocks for a new snapshot with updated and new datums
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update"), true),
                        SnapshotDatum(3, gtv("a_datum_3-update"), false),
                        SnapshotDatum(4, gtv("a_datum_4-update"), true),
                        SnapshotDatum(5, gtv("a_datum_5"), false),
                ), "b" to listOf(
                        SnapshotDatum(0, gtv("b_datum_0-update"), true),
                        SnapshotDatum(1, gtv("b_datum_1-update"), true),
                        SnapshotDatum(3, gtv("b_datum_3-update"), false),
                        SnapshotDatum(4, gtv("b_datum_4-update"), true),
                )),
                toHeight = 13
        )

        // Verify all nodes content are identical
        assertThat(nodes).hasSameSnapshotRootHash(13)
        assertThat(getSnapshotRootHash(nodes[0], c1, 13, newConfig))
                .isNotEqualTo(node0RootHash)
        assertThat(nodes).hasAllContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_datum_0-update"), true),
                SnapshotDatum(1, gtv("a_1xxxxxxxxxxxxxxxxxxxx"), false),
                SnapshotDatum(2, gtv("a_2xxxxxxxxxxxxxxxxxxxx"), true),
                SnapshotDatum(3, gtv("a_datum_3-update"), false),
                SnapshotDatum(4, gtv("a_datum_4-update"), true),
                SnapshotDatum(5, gtv("a_datum_5"), false),
        ))
        assertThat(nodes).hasAllContextSnapshotData(1, listOf(
                SnapshotDatum(0, gtv("b_datum_0-update"), true),
                SnapshotDatum(1, gtv("b_1xxxxxxxxxxxxxxxxxxxx"), false),
                SnapshotDatum(2, gtv("b_2xxxxxxxxxxxxxxxxxxxx"), true),
                SnapshotDatum(3, gtv("b_datum_3-update"), false),
                SnapshotDatum(4, gtv("b_datum_4-update"), true),
        ))
        assertThat(nodes[restartNodeIdx]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
    }

    /** With 4 nodes deploy a dapp with 2 modules. Add data for one of the modules and then restart a node to sync */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun syncEmptyContext() {
        startManagedSystem(4, 0)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        startNewBlockchain(setOf(0, 1, 2, 3), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(DEFAULT_CHAIN_IID, 1L)

        // Add data for module A
        var height = 2L
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0"), false),
                        SnapshotDatum(1, gtv("a_datum_1"), false),
                )),
                nodes,
                height
        )

        val node0RootHash = getSnapshotRootHash(nodes[0], DEFAULT_CHAIN_IID, height, config)

        assertThat(nodes).hasSameSnapshotRootHash(height, node0RootHash)

        // Restart a node and make sure it syncs successfully
        restartAndAwaitSnapshotSync(3, height)

        assertThat(nodes).hasSameSnapshotRootHash(height, node0RootHash)

        // Build a few more bocks with updated datums
        height += 2
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update"), false),
                )),
                nodes,
                toHeight = height
        )

        // Verify all nodes has the same state and root hash
        assertThat(nodes).hasSameSnapshotRootHash(height)
        assertThat(getSnapshotRootHash(nodes[0], DEFAULT_CHAIN_IID, height, config))
                .isNotEqualTo(node0RootHash)
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun syncFromSnapshotWithInitialState() {
        startManagedSystem(4, 1)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4_init_state.xml")!!.readText())
        startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Build some more blocks
        var height = 2L
        buildBlock(DEFAULT_CHAIN_IID, height)

        // Assert that our initial state is persisted to snapshot
        assertThat(nodes).hasAllContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_datum_0"), false),
                SnapshotDatum(1, gtv("a_datum_1"), true)
        ))

        // Build a few more blocks with some additional data
        height += 2
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update"), false), // overwrite initial value
                        SnapshotDatum(2, gtv("a_datum_2"), false),
                )),
                nodes,
                toHeight = height
        )

        // Verify snapshot content
        assertThat(nodes).hasAllContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_datum_0-update"), false),
                SnapshotDatum(1, gtv("a_datum_1"), true),
                SnapshotDatum(2, gtv("a_datum_2"), false)
        ))

        val node0RootHash = getSnapshotRootHash(nodes[0], DEFAULT_CHAIN_IID, height, config)

        // Assert that we could snapshot sync the chain on the replica node
        restartAndAwaitSnapshotSync(4, height)

        assertThat(nodes[4]).hasSnapshotRootHash(height, config, node0RootHash)
        assertThat(nodes[4]).hasFinalizedImportInTestModules()

        assertThat(nodes[4]).hasContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_datum_0-update"), false),
                SnapshotDatum(1, gtv("a_datum_1"), true),
                SnapshotDatum(2, gtv("a_datum_2"), false)
        ))

        // Build a few more bocks with updated datums
        height += 2
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update-2"), false),
                        SnapshotDatum(3, gtv("a_datum_3"), true),
                )),
                nodes,
                toHeight = height
        )

        // Verify all nodes has the same state and root hash
        assertThat(nodes).hasSameSnapshotRootHash(height)

        // Verify snapshot content - should match since root hash does
        assertThat(nodes).hasAllContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_datum_0-update-2"), false),
                SnapshotDatum(1, gtv("a_datum_1"), true),
                SnapshotDatum(2, gtv("a_datum_2"), false),
                SnapshotDatum(3, gtv("a_datum_3"), true)
        ))

        // Root hash should have changed since last time
        assertThat(getSnapshotRootHash(nodes[0], DEFAULT_CHAIN_IID, height, config).wrap())
                .isNotEqualTo(node0RootHash.wrap())

        assertThat(nodes[4]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf("snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a")))
    }

    /** With 4 validators:
     * 1. All nodes builds 2 blocks with some snapshot data.
     * 2. Node 3 is wiped and restarted.
     * 3. Assert that node 3 is synced with snapshot data.
     * 4. Build a few more blocks with datum updates.
     * 5. Verify that all nodes are in identical states.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun syncFromSnapshotAsValidator() {
        val datumLength = 100
        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1500 // Enforce about 10 datums per message

        startManagedSystem(4, 0)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())
        val restartNodeIdx = 3

        // Emit something here so we get some snapshot data
        val moduleDatums = mapOf(
                "a" to 50L,
                "b" to 30L,
        )
        buildInitialDatumBlocks(moduleDatums, datumLength = datumLength, toHeight = 2)
        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, config)

        // Assert that we could snapshot sync the chain on a validator node
        restartAndAwaitSnapshotSync(restartNodeIdx, 2)

        // Basic snapshot verification
        assertThat(nodes[restartNodeIdx].blockQueries().getSnapshotContextMaxIds(node0Height).get())
                .isEqualTo(mapOf(0L to 50L, 1L to 30L))

        // Build a few new blocks with updated and new datums
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update"), true),
                        SnapshotDatum(3, gtv("a_datum_3-update"), false),
                        SnapshotDatum(4, gtv("a_datum_4-update"), true),
                        SnapshotDatum(moduleDatums["a"]!! + 1L, gtv("a_datum_new_1"), false),
                        SnapshotDatum(moduleDatums["a"]!! + 2L, gtv("a_datum_new_2"), true),
                ), "b" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0-update"), true),
                        SnapshotDatum(1, gtv("a_datum_1-update"), true),
                        SnapshotDatum(moduleDatums["b"]!! + 1L, gtv("b_datum_new_1"), true),
                        SnapshotDatum(moduleDatums["b"]!! + 2L, gtv("b_datum_new_2"), false),
                )),
                toHeight = 4
        )

        val secondNode0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val secondNode0RootHash = getSnapshotRootHash(nodes[0], c1, secondNode0Height, config)

        assertThat(secondNode0Height).isGreaterThan(node0Height)
        assertThat(secondNode0RootHash).isNotEqualTo(node0RootHash)

        assertThat(nodes).hasSameSnapshotRootHash(secondNode0Height)
        assertThat(nodes[restartNodeIdx]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
        assertThat(nodes[restartNodeIdx].blockQueries().getLastBlockHeight().get()).isEqualTo(secondNode0Height)
        assertThat(nodes[restartNodeIdx].blockQueries().getSnapshotContextMaxIds(secondNode0Height).get())
                .isEqualTo(mapOf(0L to 52L, 1L to 32L))
    }

    /** With 4 validators build some blocks and snapshot, stop node 3, let node 0-2 build a few more blocks and then
     *  start node 3 and verify it won't attempt to sync snapshot (since it is on height > 0).
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun onlySyncFromHeight0() {
        val datumLength = 100
        nodeConfigurationOverrides["snapshotsync.max_data_size"] = (datumLength + 5) * 10 // Enforce about 10 datums per message

        startManagedSystem(4, 0)
        val restartNodeIdx = 3

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Emit something here so we get some snapshot data
        buildInitialDatumBlocks(mapOf("a" to 10L, "b" to 3L), datumLength = datumLength, toHeight = 2)

        nodes[restartNodeIdx].stopBlockchain(c1)

        buildBlock(nodes.subList(0, 2), c1, 6)

        nodes[restartNodeIdx].startBlockchain(c1)

        Awaitility.await().atMost(2, TimeUnit.MINUTES).untilAsserted {
            // Snapshot sync must not start
            assertThat(nodes[restartNodeIdx]).hasSnapshotSyncEvent(SnapshotSyncEvent.NO_SYNC_DUE_TO_HEIGHT_NOT_0)
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun pruneSnapshots() {
        startManagedSystem(4, 0)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4_interval_1.xml")!!.readText())
        val levelsPerPage = getBCCLevelsPerPage(config)

        startNewBlockchain(setOf(0, 1, 2, 3), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        val toHeight = 9L
        val blocks = 0..toHeight
        blocks.forEach { block ->
            buildDatumBlocks(
                    mapOf("a" to listOf(
                            SnapshotDatum(0, gtv("a_datum_0-block-$block"), false),
                    ))
            )
        }

        // Verify snapshot datums - only last 3 versions should be available
        withDatumRepository(nodes.first(), getBCCLevelsPerPage(config)) { ctx, datumRepository ->
            DatabaseAccess.of(ctx).apply {

                // Expect datum leafs states for 3 last heights (snapshots_to_Keep + 1)
                val datumHeights = blocks
                        .mapNotNull { block -> getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_0", block, 0) }
                        .map { it.blockHeight }
                assertThat(datumHeights).isEqualTo(listOf(7L, 8L, 9L))
            }

            // Verify datum values for heights
            assertThat(datumRepository.getDatum(ctx, 6L, 0L, 0L))
                    .isNull()
            assertThat(datumRepository.getDatum(ctx, 7L, 0L, 0L)?.asString())
                    .isEqualTo("a_datum_0-block-7")
            assertThat(datumRepository.getDatum(ctx, 8L, 0L, 0L)?.asString())
                    .isEqualTo("a_datum_0-block-8")
            assertThat(datumRepository.getDatum(ctx, toHeight, 0L, 0L)?.asString())
                    .isEqualTo("a_datum_0-block-$toHeight")
        }

        // All nodes should still have the same snapshot root hash
        val snapshotRootHash = getSnapshotRootHash(nodes[0], DEFAULT_CHAIN_IID, toHeight, levelsPerPage)
        assertThat(nodes).hasSameSnapshotRootHash(toHeight, snapshotRootHash)

        // Snapshot data is in expected state - now lets reset each node one by one to enforce them all to sync
        // snapshot states. This should make all nodes eventually only have the last snapshot state.
        (0..3).forEach { nodeIndex ->
            restartAndAwaitSnapshotSync(nodeIndex, toHeight)
        }

        // All nodes still has same root hash
        assertThat(nodes).hasSameSnapshotRootHash(toHeight, snapshotRootHash)

        // Verify that all nodes has the same state - one snapshot
        nodes.forEach { node ->
            withDatumRepository(node, levelsPerPage) { ctx, datumRepository ->
                DatabaseAccess.of(ctx).apply {
                    // Expect datum leafs states for 3 last heights (snapshots_to_Keep + 1)
                    val datumHeights = blocks
                            .mapNotNull { block -> getState(ctx, "${SNAPSHOT_TABLE_PREFIX}_0", block, 0) }
                            .map { it.blockHeight }
                    assertThat(datumHeights).isEqualTo(listOf(9L))
                }

                // Verify datum values for heights
                assertThat(datumRepository.getDatum(ctx, 8L, 0L, 0L))
                        .isNull()
                assertThat(datumRepository.getDatum(ctx, toHeight, 0L, 0L)?.asString())
                        .isEqualTo("a_datum_0-block-$toHeight")
            }
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun syncFromSnapshotWithSignerUpdates() {
        syncWithNewConfigTest()
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun syncFromSnapshotWithPendingSignerUpdates() {
        syncWithNewConfigTest(true)
    }

    private fun syncWithNewConfigTest(pendingConfig: Boolean = false) {
        startManagedSystem(4, 1)
        val restartNodeIdx = 4

        val initialConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(initialConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Load new config at height 5 that remove two signers (these blocks wont be possible to load with initial config)
        val newConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_updated_2.xml")!!.readText())
        addDappBlockchainConfiguration(c1, GtvEncoder.encodeGtv(newConfig), 4, pending = pendingConfig)
        buildBlockNoWait(nodes, c1, 3)
        val nodeSetups = getChainNodeSetups(c1)
        nodeSetups.subList(0, 3).forEach { awaitChainRunning(it.sequenceNumber.nodeNumber, c1, 3) }

        // Build some more blocks
        buildBlock(nodes.subList(0, 3), c1, 6)

        // Emit something here so we get some snapshot data
        buildDatumBlocks(
                mapOf("a" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0"), true),
                        SnapshotDatum(1, gtv("a_datum_1"), true),
                        SnapshotDatum(2, gtv("a_datum_2"), true),
                        SnapshotDatum(3, gtv("a_datum_3"), false),
                ), "b" to listOf(
                        SnapshotDatum(0, gtv("a_datum_0"), true),
                        SnapshotDatum(1, gtv("a_datum_1"), true),
                        SnapshotDatum(2, gtv("a_datum_2"), true),
                        SnapshotDatum(3, gtv("a_datum_3"), false),
                )),
                nodes.subList(0, 3),
        )

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()

        // Assert that we could snapshot sync the chain on the replica node
        restartAndAwaitSnapshotSync(4, node0Height)

        // Assert snapshot data is identical
        assertThat(nodes[restartNodeIdx]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `skip sync when threshold is too low`() {

        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce 1 datum per message
        nodeConfigurationOverrides["snapshotsync.threshold"] = 5

        startManagedSystem(4, 1)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildInitialDatumBlocks(mapOf(
                "a" to 5L,
                "b" to 4L,
        ))

        buildBlock(DEFAULT_CHAIN_IID, 3)

        restartNodeClean(4, c1, -1)
        val replicaNode = nodes[4]

        // Snapshot sync must start
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(replicaNode).hasSnapshotSyncEvent(SnapshotSyncEvent.NO_SYNC_DUE_TO_BELOW_THRESHOLD)
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `resumed syncing - from start`() {

        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce 1 datum per message

        startManagedSystem(4, 1)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(c1, 1)
        buildInitialDatumBlocks(mapOf(
                "a" to 5L,
                "b" to 4L,
        ))

        buildBlock(DEFAULT_CHAIN_IID)

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, config)
        val replicaNode = nodes[4]

        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            val replicaHeight = replicaNode.blockQueries().getLastBlockHeight().get()
            assertThat(replicaHeight).isEqualTo(node0Height)
            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }

        val contextDataList = getContextData(c1, config, node0Height)

        // Simulate a ongoing uncompleted sync, from the start (datum ids = 0)
        replicaNode.stopBlockchain(c1)
        withReadWriteConnection(replicaNode.postchainContext.blockBuilderStorage, c1) { ctx ->
            DatabaseAccess.of(ctx).apply {
                // Clear DB
                clearUpdatedDatums(ctx)
                pruneSnapshotSyncState(ctx)
                QueryRunner().update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_root_snapshot_pages\"")
                contextDataList.forEach {
                    QueryRunner().update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_${it.contextId}_snapshot_pages\"")
                    QueryRunner().update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_${it.contextId}_state_leafs\"")
                }

                // Insert sync state
                setSnapshotSyncState(ctx, SnapshotSyncState(node0Height, node0RootHash))
                contextDataList.forEach {
                    setSnapshotSyncContextState(ctx, SnapshotSyncContextState(it.contextId, it.rootHash,
                            0, it.datumIdMax!!))
                }
            }
            true
        }

        replicaNode.startBlockchain(c1)

        // Check that the node starts to sync and completes it with a correct root hash
        Awaitility.await().atMost(2, TimeUnit.MINUTES).untilAsserted {
            assertThat(replicaNode).hasSnapshotSyncEvent(SnapshotSyncEvent.WILL_CONTINUING_SYNC)
            assertThat(replicaNode).hasSyncedSnapshotSuccessfully()
            assertThat(replicaNode).hasFinalizedImportInTestModules()
        }

        assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        assertThat(nodes[4]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `simulate resumed syncing - with partial data`() {
        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce sending 1 datum per message

        val moduleAPart1DatumList = listOf("a_0" to false, "a_1" to false)
        val moduleBPart1DatumList = listOf("b_0" to false, "b_1" to true)

        val moduleAPart2DatumList = listOf("a_2" to false, "a_3" to true)
        val moduleBPart2DatumList = listOf("b_2" to true)

        startManagedSystem(4, 1)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(c1, 1)
        // Emit something here so we get some snapshot data
        buildDatumBlocks(
                mapOf(
                        "a" to (moduleAPart1DatumList + moduleAPart2DatumList).mapIndexed { index, pair ->
                            SnapshotDatum(index.toLong(), gtv(pair.first), pair.second)
                        },
                        "b" to (moduleBPart1DatumList + moduleBPart2DatumList).mapIndexed { index, pair ->
                            SnapshotDatum(index.toLong(), gtv(pair.first), pair.second)
                        }
                ),
        )

        assertThat(nodes).hasAllContextSnapshotData(0, listOf(
                SnapshotDatum(0, gtv("a_0"), false),
                SnapshotDatum(1, gtv("a_1"), false),
                SnapshotDatum(2, gtv("a_2"), false),
                SnapshotDatum(3, gtv("a_3"), true),
        ))
        assertThat(nodes).hasAllContextSnapshotData(1, listOf(
                SnapshotDatum(0, gtv("b_0"), false),
                SnapshotDatum(1, gtv("b_1"), true),
                SnapshotDatum(2, gtv("b_2"), true),
        ))

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, config)
        val replicaNode = nodes[4]

        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            val replicaHeight = replicaNode.blockQueries().getLastBlockHeight().get()
            assertThat(replicaHeight).isEqualTo(node0Height)
            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }

        val contextDataList = getContextData(c1, config, node0Height)

        // Simulate an ongoing uncompleted sync, from the start (datum ids = 0)
        replicaNode.stopBlockchain(c1)
        withReadWriteConnection(replicaNode.postchainContext.blockBuilderStorage, c1) { ctx ->
            DatabaseAccess.of(ctx).apply {
                // Clear DB
                clearUpdatedDatums(ctx)
                pruneSnapshotSyncState(ctx)
                with(QueryRunner()) {
                    update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_root_snapshot_pages\"")
                    contextDataList.forEach {
                        update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_${it.contextId}_snapshot_pages\"")
                        update(ctx.conn, "DELETE FROM \"c${c1}.sys.x.gtx_module_${it.contextId}_state_leafs\"")
                    }
                }

                // Insert sync state - from id 1 for both contexts
                setSnapshotSyncState(ctx, SnapshotSyncState(node0Height, node0RootHash))
                contextDataList.forEach {
                    setSnapshotSyncContextState(ctx, SnapshotSyncContextState(it.contextId, it.rootHash,
                            2, it.datumIdMax!!))
                }

                // Insert snapshot update datums up to id 1 for both contexts
                insertUpdatedDatum(ctx, 0, moduleAPart1DatumList.mapIndexed { index, pair ->
                    val gtv = gtv(pair.first)
                    DatumInfo(index.toLong(), gtv.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem)),
                            if (pair.second) null else GtvEncoder.encodeGtv(gtv))
                })
                insertUpdatedDatum(ctx, 1, moduleBPart1DatumList.mapIndexed { index, pair ->
                    val gtv = gtv(pair.first)
                    DatumInfo(index.toLong(), gtv.merkleHash(GtvMerkleHashCalculatorV2(cryptoSystem)),
                            if (pair.second) null else GtvEncoder.encodeGtv(gtv))
                })
            }
            true
        }

        replicaNode.startBlockchain(c1)

        // Check that the node starts to sync and the root hash matches
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(replicaNode).hasSnapshotSyncEvent(SnapshotSyncEvent.WILL_CONTINUING_SYNC)
            assertThat(replicaNode).hasSyncedSnapshotSuccessfully()
        }

        assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)

        assertThat(replicaNode).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
    }

    /** For manual testing */
    @Test
    @Disabled
    fun syncMoreData() {
        nodeConfigurationOverrides["snapshotsync.max_time"] = 5_000
        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1024 * 1024 * 1
//        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 300

        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4_lpp_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(c1, 9)

        buildInitialDatumBlocks(datumLength = 200, chunksPerBlock = 5000, moduleDatums = mapOf(
                "a" to 25_000L,
                "b" to 5_000L
        ))

        buildBlock(DEFAULT_CHAIN_IID)

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()

        // Assert that we could snapshot sync the chain on the replica node
        restartAndAwaitSnapshotSync(4, node0Height)

        assertThat(nodes[4]).hasIdenticalTableContentAs(nodes[0],
                basicTableContentProvider(listOf(
                        "snapshot_test_datums_module_a",
                        "snapshot_test_permanent_datums_module_a",
                        "snapshot_test_datums_module_b",
                        "snapshot_test_permanent_datums_module_b"
                )))
    }

    /** Generate datums and blocks based on input config */
    private fun buildInitialDatumBlocks(
            moduleDatums: Map<String, Long>,
            chunksPerBlock: Int = Int.MAX_VALUE,
            datumLength: Int = 200,
            toHeight: Long? = null,
    ) {
        (0..(moduleDatums.values.max())).chunked(chunksPerBlock).forEach { range ->
            buildDatumBlock { txBuilder ->
                range.forEach {
                    moduleDatums.forEach { (module, count) ->
                        if (it <= count) {
                            txBuilder.addOperation("emit_datum_$module", gtv(it), gtv("${module}_$it" + "x".repeat(datumLength)), gtv(it % 2 == 0L))
                        }
                    }
                }
            }
        }

        if (toHeight != null) {
            buildBlock(DEFAULT_CHAIN_IID, toHeight)
        }
    }

    /** Build a block with given datums */
    private fun buildDatumBlocks(
            moduleDatums: Map<String, List<SnapshotDatum>>,
            nodes: List<PostchainTestNode> = getChainNodes(DEFAULT_CHAIN_IID),
            toHeight: Long? = null,
    ) {
        buildDatumBlock(nodes) { txBuilder ->
            moduleDatums.forEach { (module, datums) ->
                datums.forEach {
                    txBuilder.addOperation("emit_datum_$module", gtv(it.id), it.data, gtv(it.isPermanent))
                }
            }
        }

        if (toHeight != null) {
            buildBlock(nodes, DEFAULT_CHAIN_IID, toHeight)
        }
    }

    /** Build a block with lambda tx builder */
    private fun buildDatumBlock(
            nodes: List<PostchainTestNode> = getChainNodes(DEFAULT_CHAIN_IID),
            builder: (GtxBuilder) -> Unit
    ) {
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()

        buildBlock(nodes, DEFAULT_CHAIN_IID,
                transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(),
                        cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem)).apply(builder).finish().buildGtx().encode()))
    }

    private fun getContextData(chainId: Long, config: Gtv, height: Long): List<SnapshotBlockHeaderContextData> {
        return withReadConnection(nodes[0].postchainContext.blockBuilderStorage, chainId) { ctx ->
            val rootSnapshotStore = SnapshotPageStore(ctx, getBCCLevelsPerPage(config), 0,
                    SimpleDigestSystem(cryptoSystem), "${SNAPSHOT_TABLE_PREFIX}_root")
            val contextRootHashes = rootSnapshotStore.getAllLeafHashes(height)
            nodes[0].blockQueries(chainId).getSnapshotContextMaxIds(height).get().map {
                SnapshotBlockHeaderContextData(it.key, contextRootHashes[it.key.toInt()], it.value)
            }
        }
    }
}

fun Assert<PostchainTestNode>.hasFinalizedImportInTestModules() = given { node ->
    val modulesSynced = node.getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getSnapshotAwareModules()
            .filterIsInstance<SnapshotTestModule>()
            .map { it.finalizeImportCalled }
    if (modulesSynced.isEmpty()) {
        expected("to contain any ${SnapshotTestModule::class.java.name} module")
    }
    if (modulesSynced.any { !it }) {
        expected("to have a ${SnapshotTestModule::class.java.name} module called with sync data")
    }
}
