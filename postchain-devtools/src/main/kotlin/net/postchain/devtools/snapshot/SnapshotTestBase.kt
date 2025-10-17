package net.postchain.devtools.snapshot

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.support.expected
import net.postchain.base.configuration.KEY_SNAPSHOT
import net.postchain.base.configuration.KEY_SNAPSHOT_INTERVAL
import net.postchain.base.configuration.KEY_SNAPSHOT_LEVELS_PER_PAGE
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.BaseSnapshotDatumRepository
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotBlockchainConfigurationData
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.base.withReadConnection
import net.postchain.base.withReadWriteConnection
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.EContext
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.ebft.syncmanager.common.SnapshotSyncEvent
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.gtv.Gtv
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for integration tests that use snapshot sync with some helpers to help assert results.
 * A basic module snapshot sync test would be:
 *  1. Start 3 signer nodes.
 *  2. Build blocks with data.
 *  3. Do a clean restart of one signer node. [restartAndAwaitSnapshotSync], [restartNodeClean]
 *  4. Wait for it to sync snapshot. [restartAndAwaitSnapshotSync], [hasSyncedSnapshotSuccessfully]
 *  5. Assert that the snapshot is identical on all nodes. [hasSameSnapshotRootHash], [hasIdenticalTableContentAs]
 *
 * This base class disables snapshot threshold by default to trigger snapshot sync even for low heights.
 */
open class SnapshotTestBase() : ManagedModeTest() {

    val nodeConfigurationOverrides = mutableMapOf<String, Any>()

    @BeforeEach
    fun beforeEach() {
        nodeConfigurationOverrides["snapshotsync.threshold"] = 0 // Always sync by default
        nodeConfigurationOverrides["fastsync.job_timeout"] = 2000 // Reduce time for detecting peer snapshots
    }

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeConfigurationOverrides.forEach { (key, value) -> nodeSetup.nodeSpecificConfigs.setProperty(key, value) }
    }

    fun withDatumRepository(node: PostchainTestNode, levelsPerPage: Int, action: (ctx: EContext, datumRepository: SnapshotDatumRepository) -> Unit) {
        val node0Modules = node.getBlockchainInstance(DEFAULT_CHAIN_IID)
                .blockchainEngine.getConfiguration().getSnapshotAwareModules()
        val datumRepository = BaseSnapshotDatumRepository(node0Modules, levelsPerPage, node.postchainContext.cryptoSystem)

        withReadWriteConnection(node.postchainContext.blockBuilderStorage, DEFAULT_CHAIN_IID) { ctx ->
            action(ctx, datumRepository)
        }
    }

    fun getBCCLevelsPerPage(config: Gtv) =
            config[KEY_SNAPSHOT]?.get(KEY_SNAPSHOT_LEVELS_PER_PAGE)?.asInteger()?.toInt()
                    ?: SnapshotBlockchainConfigurationData.default.levelsPerPage

    fun getBCCInterval(config: Gtv) =
            config[KEY_SNAPSHOT]?.get(KEY_SNAPSHOT_INTERVAL)?.asInteger()
                    ?: SnapshotBlockchainConfigurationData.default.snapshotInterval

    fun getSnapshotRootHash(node: PostchainTestNode, chainId: Long, height: Long, config: Gtv): Hash {
        return getSnapshotRootHash(node, chainId, height, getBCCLevelsPerPage(config))
    }

    fun getSnapshotRootHash(node: PostchainTestNode, chainId: Long, height: Long, levelsPerPage: Int): Hash {
        val replicaRootHash = withReadConnection(node.postchainContext.blockBuilderStorage, chainId) { ctx ->
            SnapshotPageStore(ctx, levelsPerPage, 0, SimpleDigestSystem(node.appConfig.cryptoSystem),
                    "${SNAPSHOT_TABLE_PREFIX}_root")
                    .getRootHashAtHeight(height)
        }
        return replicaRootHash
    }

    /** Restart a node clean (wipe db) and verify it sync successfully */
    fun restartAndAwaitSnapshotSync(nodeIndex: Int, expectedHeight: Long) {
        restartNodeClean(nodeIndex, DEFAULT_CHAIN_IID, -1)

        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(nodes[nodeIndex].blockQueries().getLastBlockHeight().get()).isEqualTo(expectedHeight)
            assertThat(nodes[nodeIndex]).hasSnapshotSyncEvent(SnapshotSyncEvent.SYNCING)
            assertThat(nodes[nodeIndex]).hasSyncedSnapshotSuccessfully()
        }
        assertThat(nodes).hasSameSnapshotRootHash(expectedHeight)
    }

    fun getChainConfig(node: PostchainTestNode) =
            node.getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().rawConfig

    fun Assert<List<PostchainTestNode>>.hasAllContextSnapshotData(contextId: Long, expectedDatums: List<SnapshotDatum>) = given { nodes ->
        nodes.forEach {
            assertk.assertThat(it).hasContextSnapshotData(contextId, expectedDatums)
        }
    }

    fun Assert<PostchainTestNode>.hasContextSnapshotData(contextId: Long, expectedDatums: List<SnapshotDatum>) = given { node ->
        withDatumRepository(node, 0) { ctx, datumRepository ->
            val data = datumRepository.getDatums(ctx, Long.MAX_VALUE, contextId, 0, Long.MAX_VALUE, Long.MAX_VALUE)
            if (data != expectedDatums) {
                expected("to be identical on node ${nodes.indexOf(node)}", expectedDatums, data)
            }
        }
    }

    fun Assert<PostchainTestNode>.hasSyncedSnapshotSuccessfully() = given { node ->
        assertk.assertThat(node).hasSnapshotSyncEvent(SnapshotSyncEvent.FINISHED_SUCCESSFULLY)
    }

    fun Assert<PostchainTestNode>.hasSnapshotRootHash(height: Long, config: Gtv, expectedSnapshotRootHash: Hash) = given { node ->
        if (!getSnapshotRootHash(node, DEFAULT_CHAIN_IID, height, config).contentEquals(expectedSnapshotRootHash)) {
            expected("to have snapshot root hash $expectedSnapshotRootHash at height $height")
        }
    }

    fun Assert<List<PostchainTestNode>>.hasSameSnapshotRootHash(height: Long, snapshotRootHash: Hash? = null) = given { nodes ->
        val rootHashes = mutableSetOf<WrappedByteArray>()
        nodes.forEach { node ->
            rootHashes.add(getSnapshotRootHash(node, DEFAULT_CHAIN_IID, height, getChainConfig(node)).wrap())
        }
        if (rootHashes.size > 1) {
            expected("to have the same snapshot root hash")
        }
        snapshotRootHash?.apply {
            if (!rootHashes.contains(this.wrap())) {
                expected("to have snapshot root hash $snapshotRootHash")
            }
        }
    }

    fun Assert<List<PostchainTestNode>>.hasSameState(stateProvider: (PostchainTestNode) -> Any) = given { nodes ->
        val states = nodes.associate { nodes.indexOf(it) to stateProvider(it) }
        if (states.values.toSet().size != 1) {
            val statesString = states.map { "${it.key}=${it.value}" }.joinToString(", ")
            expected("to have the same state: $statesString")
        }
    }

    /** Get and verify db content is identical between two nodes.
     *  @param snapshotNode The node to get data from and compare against the node in the assertThat.
     *  @param contentProvider Provider function to compile a view of the database content.
     */
    fun Assert<PostchainTestNode>.hasIdenticalTableContentAs(
            snapshotNode: PostchainTestNode,
            contentProvider: (EContext) -> Map<String, Any>
    ) = given { node ->
        val expected = withReadConnection(node.postchainContext.blockBuilderStorage, DEFAULT_CHAIN_IID) { ctx ->
            contentProvider(ctx)
        }
        val actual = withReadConnection(snapshotNode.postchainContext.blockBuilderStorage, DEFAULT_CHAIN_IID) { ctx ->
            contentProvider(ctx)
        }
        if (expected != actual) {
            expected("to have identical table content for node ", expected, actual)
        }
    }

    /** Table content provider to use with [hasIdenticalTableContentAs] to get a basic summary of the provided list
     * of tables. All values are converted to strings, except for byte arrays which are converted to hex strings.
     */
    fun basicTableContentProvider(tables: List<String>): (EContext) -> Map<String, Any> = { ctx ->
        tables.associateWith { table ->
            val tableName = DatabaseAccess.of(ctx).tableName(table)
            ctx.conn.createStatement().executeQuery("SELECT * FROM $tableName").use { rs ->
                val columns = (1..rs.metaData.columnCount).map {
                    rs.metaData.getColumnName(it) to rs.metaData.getColumnTypeName(it)
                }
                val results = mutableListOf<Map<String, Any>>()
                while (rs.next()) {
                    val row = columns.map { (name, type) ->
                        name to if (type == "bytea") {
                            rs.getBytes(name).toHex()
                        } else {
                            rs.getString(name)
                        }
                    }
                    results.add(row.toMap())
                }
                results.toList()
            }
        }
    }

    /** Table content provider to use with [hasIdenticalTableContentAs] to get a basic summary of the provided list
     * of tables using the chain prefix (`cX`). All values are converted to strings, except for byte arrays which are converted to hex strings.
     */
    fun basicChainTableContentProvider(chainTables: List<String>): (EContext) -> Map<String, Any> = { ctx ->
        basicTableContentProvider(chainTables.map {
            DatabaseAccess.of(ctx).tableName(ctx, it).replace("\"", "")
        }).invoke(ctx)
    }
}

fun Assert<PostchainTestNode>.hasSnapshotSyncEvent(event: SnapshotSyncEvent) = given { node ->
    val events = when (val blockchainProcessor = node.getBlockchainInstance(DEFAULT_CHAIN_IID)) {
        is ValidatorBlockchainProcess -> {
            blockchainProcessor.syncManager.getSnapshotSyncEvents()
        }
        is ReadOnlyBlockchainProcess -> {
            blockchainProcessor.getSnapshotSyncEvents()
        }
        else -> {
            expected("Unsupported blockchain process type: ${node.getBlockchainInstance(DEFAULT_CHAIN_IID)::class.java.name}")
        }
    }

    if (!events.events.contains(event)) {
        expected("to contain event $event")
    }
}
