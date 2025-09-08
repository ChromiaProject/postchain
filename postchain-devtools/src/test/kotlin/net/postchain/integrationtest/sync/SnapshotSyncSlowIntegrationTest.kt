package net.postchain.integrationtest.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatumInfo
import net.postchain.base.data.SnapshotSyncContextState
import net.postchain.base.data.SnapshotSyncState
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotBlockchainConfigurationData
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.common.createLogCaptor
import net.postchain.common.data.Hash
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.ebft.message.SnapshotBlockHeaderContextData
import net.postchain.ebft.syncmanager.common.SnapshotSynchronizer
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import org.apache.commons.dbutils.QueryRunner
import org.apache.logging.log4j.core.test.appender.ListAppender
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SnapshotSyncSlowIntegrationTest : ManagedModeTest() {

    private val nodeConfigurationOverrides = mutableMapOf<String, Any>()

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("snapshotsync.threshold", 5)
        nodeConfigurationOverrides.forEach { (key, value) -> nodeSetup.nodeSpecificConfigs.setProperty(key, value) }
    }

    @Test
    fun syncFromSnapshot() {
        startManagedSystem(4, 1, restApi = true)

        val initialConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(initialConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Load new config at height 5
        val newConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_updated_4.xml")!!.readText())
        addDappBlockchainConfiguration(c1, GtvEncoder.encodeGtv(newConfig), 5)
        buildBlockNoWait(nodes.subList(0, 3),c1, 4)
        val nodeSetups = getChainNodeSetups(c1)
        nodeSetups.subList(0, 3).forEach { awaitChainRunning(it.sequenceNumber.nodeNumber, c1, 4) }

        // Build some more blocks
        buildBlock(nodes.subList(0, 3),c1, 9)
        // Emit something here so we get some snapshot data
        val moduleDatums = mapOf(
                "a" to 3L,
                "b" to 3L,
        )
        buildDatumBlocks(moduleDatums)

        // Assert that we could snapshot sync the chain on the replica node
        restartNodeClean(4, c1, -1)
        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            val height = nodes[4].blockQueries().getLastBlockHeight().get()
            assertThat(height).isEqualTo(10)

            // Assert snapshot data is identical
            assertThat(nodes[4].blockQueries().getSnapshotContextMaxIds(height).get().values.filterNotNull())
                    .isEqualTo(listOf(3L, 3L))
        }

        // Build enough blocks for a new snapshot with updated and new datums
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()
        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, 13,
                transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                        // For module A
                        .addOperation("emit_datum_a", gtv(0), gtv("a_datum_0-update-1"), gtv(true))
                        .addOperation("emit_datum_a", gtv(3), gtv("a_datum_3-update-1"), gtv(false))
                        .addOperation("emit_datum_a", gtv(4), gtv("a_datum_4"), gtv(true))
                        .addOperation("emit_datum_a", gtv(5), gtv("a_datum_5"), gtv(false))
                        // For module B
                        .addOperation("emit_datum_b", gtv(0), gtv("b_datum_0-update-1"), gtv(true))
                        .addOperation("emit_datum_b", gtv(1), gtv("b_datum_1-update-1"), gtv(true))
                        .addOperation("emit_datum_b", gtv(3), gtv("b_datum_3-update-1"), gtv(false))
                        .addOperation("emit_datum_b", gtv(4), gtv("b_datum_4"), gtv(true))
                        .finish()
                        .buildGtx()
                        .encode()))

        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            assertThat(nodes[0].blockQueries().getLastBlockHeight().get()).isEqualTo(13)
            assertThat(nodes[4].blockQueries().getLastBlockHeight().get()).isEqualTo(13)

            // Assert snapshot data is identical
            assertThat(nodes[4].blockQueries().getSnapshotContextMaxIds(14).get().values.filterNotNull())
                    .isEqualTo(listOf(5L, 4L))
        }
    }

    @Test
    fun syncFromSnapshotWithSignerUpdates() {
        syncWithNewConfigTest()
    }

    @Test
    fun syncFromSnapshotWithPendingSignerUpdates() {
        syncWithNewConfigTest(true)
    }

    private fun syncWithNewConfigTest(pendingConfig: Boolean = false) {
        startManagedSystem(4, 1)

        val initialConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(initialConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        // Load new config at height 5 that remove two signers (these blocks wont be possible to load with initial config)
        val newConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_updated_2.xml")!!.readText())
        addDappBlockchainConfiguration(c1, GtvEncoder.encodeGtv(newConfig), 4, pending = pendingConfig)
        buildBlockNoWait(nodes.subList(0, 3), c1, 3)
        val nodeSetups = getChainNodeSetups(c1)
        nodeSetups.subList(0, 3).forEach { awaitChainRunning(it.sequenceNumber.nodeNumber, c1, 3) }

        // Build some more blocks
        buildBlock(nodes.subList(0, 3), c1, 6)

        // Emit something here so we get some snapshot data
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()
        val emitDatumsTx = transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                // For module A
                .addOperation("emit_datum_a", gtv(0), gtv("a_datum_0"), gtv(true))
                .addOperation("emit_datum_a", gtv(1), gtv("a_datum_1"), gtv(true))
                .addOperation("emit_datum_a", gtv(2), gtv("a_datum_2"), gtv(true))
                .addOperation("emit_datum_a", gtv(3), gtv("a_datum_3"), gtv(false))
                // For module B
                .addOperation("emit_datum_b", gtv(0), gtv("b_datum_0"), gtv(true))
                .addOperation("emit_datum_b", gtv(1), gtv("b_datum_1"), gtv(true))
                .addOperation("emit_datum_b", gtv(2), gtv("b_datum_2"), gtv(true))
                .addOperation("emit_datum_b", gtv(3), gtv("b_datum_3"), gtv(false))
                .finish()
                .buildGtx()
                .encode()
        )

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, emitDatumsTx)

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, newConfig)

        // Assert that we could snapshot sync the chain on the replica node
        restartNodeClean(4, c1, -1)
        val replicaNode = nodes[4]

        Awaitility.await().atMost(Duration.FIVE_MINUTES).untilAsserted {
            val height = replicaNode.blockQueries().getLastBlockHeight().get()
            assertThat(height).isEqualTo(7)

            // Assert snapshot data is identical
            assertThat(replicaNode.blockQueries().getSnapshotContextMaxIds(height).get().values.filterNotNull())
                    .isEqualTo(listOf(3L, 3L))

            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, newConfig)).isEqualTo(node0RootHash)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `skip sync when threshold is too low`() {

        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce 1 datum per message
        nodeConfigurationOverrides["snapshotsync.threshold"] = 5

        val appender = createLogCaptor(SnapshotSynchronizer::class.java, "List")
        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        val moduleDatums = mapOf(
                "a" to 5L,
                "b" to 4L,
        )
        buildDatumBlocks(moduleDatums)

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, 3)

        restartNodeClean(4, c1, -1)
        val replicaNode = nodes[4]

        // Snapshot sync must start
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(appender.eventsForNodeContains(replicaNode, "Snapshot height 3 is below threshold 5. Not syncing snapshot."))
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `simulate resumed syncing - from start`() {

        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce 1 datum per message
        nodeConfigurationOverrides["snapshotsync.threshold"] = 0

        val appender = createLogCaptor(SnapshotSynchronizer::class.java, "List")
        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(nodes.subList(0, 3), c1, 1)
        val moduleDatums = mapOf(
                "a" to 5L,
                "b" to 4L,
        )
        buildDatumBlocks(moduleDatums)

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID)

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

        appender.clear()
        replicaNode.startBlockchain(c1)

        // Check that the node starts to sync and completes it with a correct root hash
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(appender.eventsForNodeContains(replicaNode, "Continuing snapshot sync for height $node0Height with context offsets: [(0, 0), (1, 0)]")).isTrue()
            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `simulate resumed syncing - with partial data`() {

        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1 // Enforce 1 datum per message
        nodeConfigurationOverrides["snapshotsync.threshold"] = 0

        val moduleAPart1DatumList = listOf("a_0" to false, "a_1" to false)
        val moduleBPart1DatumList = listOf("b_0" to false, "b_1" to true)

        val moduleAPart2DatumList = listOf("a_2" to false, "a_3" to true)
        val moduleBPart2DatumList = listOf("b_2" to true)

        val appender = createLogCaptor(SnapshotSynchronizer::class.java, "List")
        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(nodes.subList(0, 3), c1, 1)
        // Emit something here so we get some snapshot data
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                .apply {
                    (moduleAPart1DatumList + moduleAPart2DatumList).forEachIndexed { index, pair ->
                        addOperation("emit_datum_a", gtv(index.toLong()), gtv(pair.first), gtv(pair.second))
                    }
                    (moduleBPart1DatumList + moduleBPart2DatumList).forEachIndexed { index, pair ->
                        addOperation("emit_datum_b", gtv(index.toLong()), gtv(pair.first), gtv(pair.second))
                    }
                }
                .finish()
                .buildGtx()
                .encode()
        ))

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID)

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

        appender.clear()
        replicaNode.startBlockchain(c1)

        // Check that the node starts to sync and the root hash matches
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(appender.eventsForNodeContains(replicaNode, "Continuing snapshot sync for height $node0Height with context offsets: [(0, 2), (1, 2)]")).isTrue()
            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }
    }

    @Test
    @Disabled // TODO just for manual tests, remove or move out?
    fun syncMoreData() {

        nodeConfigurationOverrides["snapshotsync.max_time"] = 5_000
//        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1024 * 1024 * 1
        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 300

        val appender = createLogCaptor(SnapshotSynchronizer::class.java, "List")
        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4_lpp_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(nodes.subList(0, 3),c1, 9)

        val moduleDatums = mapOf(
                "a" to 25_000L,
                "b" to 5_000L
        )
        buildDatumBlocks(moduleDatums, 5000, 200)

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID)

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, config)

        // Assert that we could snapshot sync the chain on the replica node
        restartNodeClean(4, c1, -1)
        val replicaNode = nodes[4]

        // Snapshot sync must start
        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            assertThat(appender.eventsForNodeContains(replicaNode, "Snapshot sync starts from nodes"))
        }

        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            val replicaHeight = replicaNode.blockQueries().getLastBlockHeight().get()
            assertThat(replicaHeight).isEqualTo(node0Height)
            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }
    }

    private fun buildDatumBlocks(
            moduleDatums: Map<String, Long>,
            chunksPerBlock: Int = Int.MAX_VALUE,
            datumLength: Int = 200,
    ) {
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()

        (0..(moduleDatums.values.max())).chunked(chunksPerBlock).forEach { range ->
            buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                    .apply {
                        range.forEach {
                            moduleDatums.forEach { (module, count) ->
                                if (it <= count) {
                                    addOperation("emit_datum_$module", gtv(it), gtv("a_$it" + "x".repeat(datumLength)), gtv(it % 2 == 0L))
                                }
                            }
                        }
                    }
                    .finish()
                    .buildGtx()
                    .encode()
            ))
        }
    }

    private fun getSnapshotRootHash(node: PostchainTestNode, chainId: Long, node0Height: Long, config: Gtv): Hash {
        val replicaRootHash = withReadConnection(node.postchainContext.blockBuilderStorage, chainId) { ctx ->
            SnapshotPageStore(ctx, getLevelsPerPage(config), 0, SimpleDigestSystem(node.appConfig.cryptoSystem),
                    "${SNAPSHOT_TABLE_PREFIX}_root")
                    .getRootHashAtHeight(node0Height)
        }
        return replicaRootHash
    }

    private fun getLevelsPerPage(config: Gtv) =
            config["snapshot"]?.get("levels_per_page")?.asInteger()?.toInt()
                    ?: SnapshotBlockchainConfigurationData.default.levelsPerPage

    private fun ListAppender.eventsForNode(node: PostchainTestNode) =
            events.filter { it.contextData.getValue<String>("node.pubkey") == node.appConfig.pubKey  }

    private fun ListAppender.eventsForNodeContains(node: PostchainTestNode, message: String) =
            eventsForNode(node).any { it.message.toString().contains(message) }

    private fun getContextData(chainId: Long, config: Gtv, height: Long): List<SnapshotBlockHeaderContextData> {
        return withReadConnection(nodes[0].postchainContext.blockBuilderStorage, chainId) { ctx ->
            val rootSnapshotStore = SnapshotPageStore(ctx, getLevelsPerPage(config), 0,
                    SimpleDigestSystem(cryptoSystem), "${SNAPSHOT_TABLE_PREFIX}_root")
            val contextRootHashes = rootSnapshotStore.getAllLeafHashes(height)
            nodes[0].blockQueries(chainId).getSnapshotContextMaxIds(height).get().map {
                SnapshotBlockHeaderContextData(it.key, contextRootHashes[it.key.toInt()], it.value)
            }
        }
    }
}
