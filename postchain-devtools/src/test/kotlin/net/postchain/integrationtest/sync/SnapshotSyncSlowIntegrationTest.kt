package net.postchain.integrationtest.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockEContext
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotBlockchainConfigurationData
import net.postchain.base.snapshot.SnapshotPageStore
import net.postchain.base.withReadWriteConnection
import net.postchain.common.data.Hash
import net.postchain.concurrent.util.get
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

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
    @Disabled // TODO just for manual tests, remove or move out?
    fun syncMoreData() {

        nodeConfigurationOverrides["snapshotsync.max_time"] = 8_000
//        nodeConfigurationOverrides["snapshotsync.max_data_size"] = 1024 * 1024 * 1
        val datumLength = 200
        val dataItemsPerContext = 50_000L // per context
        val chunks = 5000

        startManagedSystem(4, 1, restApi = true)

        val config = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/snapshot/blockchain_config_4_lpp_4.xml")!!.readText())
        val c1 = startNewBlockchain(setOf(0, 1, 2, 3), setOf(4), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(config), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(nodes.subList(0, 3),c1, 9)
        // Emit something here so we get some snapshot data
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()

        (0..dataItemsPerContext).chunked(chunks).forEach { range ->
            buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID, transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                    .apply {
                        range.forEach {
                            addOperation("emit_datum_a", gtv(it), gtv("a_$it" + "x".repeat(datumLength)), gtv(it % 2 == 0L))
                            addOperation("emit_datum_b", gtv(it), gtv("b_$it" + "x".repeat(datumLength)), gtv(it % 2 == 0L))
                        }
                    }
                    .finish()
                    .buildGtx()
                    .encode()
            ))
        }

        buildBlock(nodes.subList(0, 3), DEFAULT_CHAIN_IID)

        val node0Height = nodes[0].blockQueries().getLastBlockHeight().get()
        val node0RootHash = getSnapshotRootHash(nodes[0], c1, node0Height, config)

        // Assert that we could snapshot sync the chain on the replica node
        restartNodeClean(4, c1, -1)
        val replicaNode = nodes[4]

        Awaitility.await().atMost(Duration.TEN_MINUTES).untilAsserted {
            val replicaHeight = replicaNode.blockQueries().getLastBlockHeight().get()
            assertThat(replicaHeight).isEqualTo(node0Height)

            assertThat(getSnapshotRootHash(replicaNode, c1, node0Height, config)).isEqualTo(node0RootHash)
        }
    }

    private fun getSnapshotRootHash(replicaNode: PostchainTestNode, c1: Long, node0Height: Long, config: Gtv): Hash {
        val levelsPerPage = config["snapshot"]?.get("levels_per_page")?.asInteger()?.toInt() ?: SnapshotBlockchainConfigurationData.default.levelsPerPage
        val replicaRootHash = withReadWriteConnection(replicaNode.postchainContext.blockBuilderStorage, c1) { ctx ->
            val bctx = BaseBlockEContext(ctx, node0Height, -1, -1, mapOf()) { _, _, _ -> }
            SnapshotPageStore(bctx, levelsPerPage, 0, SimpleDigestSystem(replicaNode.appConfig.cryptoSystem),
                    "${SNAPSHOT_TABLE_PREFIX}_root")
                    .getRootHashAtHeight(node0Height)
        }
        return replicaRootHash
    }
}
