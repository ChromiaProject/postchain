package net.postchain.integrationtest.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isZero
import assertk.isContentEqualTo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.BaseSnapshotDatumRepository
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.data.EMPTY_HASH
import net.postchain.concurrent.util.get
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.getModules
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.SnapshotAware
import net.postchain.gtx.SnapshotContext
import net.postchain.gtx.data.ExtOpData
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.AbstractListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.security.MessageDigest
import java.sql.ResultSet

class SnapshotTest : IntegrationTestSetup() {

    @Test
    fun snapshotTest() {
        configOverrides.addProperty("fastsync.job_timeout", "10")
        val nodes = createNodes(4, "/net/postchain/devtools/snapshot/blockchain_config_4.xml")
        val hashCalc = GtvMerkleHashCalculatorV2(cryptoSystem)

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

        buildBlock(DEFAULT_CHAIN_IID, emitDatumsTx)
        // Assert that we only write snapshot every second block according to interval
        val blockQueries = nodes[0].blockQueries(DEFAULT_CHAIN_IID)
        val block0Header = BlockHeaderData.fromBinary(blockQueries.getBlockAtHeight(0).get()!!.header.rawData)
        assertThat(block0Header.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]).isNull()
        // Build next block to trigger snapshot
        buildBlock(DEFAULT_CHAIN_IID)

        // Assert correct state hash was added to block header
        val digestSystem = SimpleDigestSystem(MessageDigest.getInstance("SHA-256"))
        val leftA = digestSystem.hash(gtv("a_datum_0").merkleHash(hashCalc), gtv("a_datum_1").merkleHash(hashCalc))
        val rightA = digestSystem.hash(gtv("a_datum_2").merkleHash(hashCalc), gtv("a_datum_3").merkleHash(hashCalc))
        val rootA = digestSystem.hash(leftA, rightA)

        val leftB = digestSystem.hash(gtv("b_datum_0").merkleHash(hashCalc), gtv("b_datum_1").merkleHash(hashCalc))
        val rightB = digestSystem.hash(gtv("b_datum_2").merkleHash(hashCalc), gtv("b_datum_3").merkleHash(hashCalc))
        val rootB = digestSystem.hash(leftB, rightB)
        val expectedRootHash = digestSystem.hash(
                digestSystem.hash(rootA, rootB),
                digestSystem.hash(EMPTY_HASH, EMPTY_HASH) // Right side of root state tree is empty since we have levelsPerPage = 2 and only two leafs
        )

        val block1Header = BlockHeaderData.fromBinary(blockQueries.getBlockAtHeight(1).get()!!.header.rawData)
        assertThat(block1Header.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]!!.asByteArray()).isContentEqualTo(expectedRootHash)

        // Assert that permanent and non-permanent datums can be recovered
        val datumRepository = BaseSnapshotDatumRepository(nodes[0].getModules().filterIsInstance<SnapshotAware>(),
                2, cryptoSystem)

        withReadConnection(nodes[0].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
            // Permanent
            val datumA0 = datumRepository.getDatum(ctx, 1, 0, 0)
            val datumB0 = datumRepository.getDatum(ctx, 1, 1, 0)

            assertThat(datumA0).isEqualTo(gtv("a_datum_0"))
            assertThat(datumB0).isEqualTo(gtv("b_datum_0"))

            // Non-permanent
            val datumA3 = datumRepository.getDatum(ctx, 1, 0, 3)
            val datumB3 = datumRepository.getDatum(ctx, 1, 1, 3)

            assertThat(datumA3).isEqualTo(gtv("a_datum_3"))
            assertThat(datumB3).isEqualTo(gtv("b_datum_3"))
        }

        val emitNewDatumsTx = transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                // Overwrite non-permanent datum For module A
                .addOperation("emit_datum_a", gtv(3), gtv("a_datum_3_v2"), gtv(false))
                // Overwrite non-permanent datum For module B
                .addOperation("emit_datum_b", gtv(3), gtv("b_datum_3_v2"), gtv(false))
                .finish()
                .buildGtx()
                .encode()
        )

        buildBlock(DEFAULT_CHAIN_IID, emitNewDatumsTx)
        buildBlock(DEFAULT_CHAIN_IID) // Build an extra block to trigger new snapshot

        val newRightA = digestSystem.hash(gtv("a_datum_2").merkleHash(hashCalc), gtv("a_datum_3_v2").merkleHash(hashCalc))
        val newRootA = digestSystem.hash(leftA, newRightA)
        val newRightB = digestSystem.hash(gtv("b_datum_2").merkleHash(hashCalc), gtv("b_datum_3_v2").merkleHash(hashCalc))
        val newRootB = digestSystem.hash(leftB, newRightB)
        val newExpectedRootHash = digestSystem.hash(
                digestSystem.hash(newRootA, newRootB),
                digestSystem.hash(EMPTY_HASH, EMPTY_HASH)
        )

        val block3Header = BlockHeaderData.fromBinary(blockQueries.getBlockAtHeight(3).get()!!.header.rawData)
        assertThat(block3Header.getExtra()[SNAPSHOT_ROOT_EXTRA_HEADER]!!.asByteArray()).isContentEqualTo(newExpectedRootHash)

        withReadConnection(nodes[0].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
            // Assert still the same value at height 0
            val datumA3V1 = datumRepository.getDatum(ctx, 1, 0, 3)
            val datumB3V1 = datumRepository.getDatum(ctx, 1, 1, 3)

            assertThat(datumA3V1).isEqualTo(gtv("a_datum_3"))
            assertThat(datumB3V1).isEqualTo(gtv("b_datum_3"))

            // Assert new value at height 1
            val datumA3V2 = datumRepository.getDatum(ctx, 3, 0, 3)
            val datumB3V2 = datumRepository.getDatum(ctx, 3, 1, 3)

            assertThat(datumA3V2).isEqualTo(gtv("a_datum_3_v2"))
            assertThat(datumB3V2).isEqualTo(gtv("b_datum_3_v2"))
        }
    }

    /**
     * Start 4 nodes and add some module data to build snapshot data. Clear module tables on node 4 and restore it from node 1.
     * Verify the module table data is identical.
     */
    @ParameterizedTest
    @ValueSource(longs = [1L, 100L, Long.MAX_VALUE])
    fun reconstructSnapshotDataTest(maxDataSize: Long) {
        configOverrides.addProperty("fastsync.job_timeout", "10")
        val nodes = createNodes(4, "/net/postchain/devtools/snapshot/blockchain_config_4.xml")
        val brid = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.blockchainRid
        val transactionFactory = nodes[0].getBlockchainInstance(DEFAULT_CHAIN_IID).blockchainEngine.getConfiguration().getTransactionFactory()

        val emitDatumsTx = transactionFactory.decodeTransaction(GtxBuilder(brid, emptyList(), cryptoSystem, GtvMerkleHashCalculatorV2(cryptoSystem))
                .apply {
                    (0..20L).forEach {
                        addOperation("emit_datum_a", gtv(it), gtv("a_datum_$it"), gtv(it % 4 == 0L))
                        addOperation("emit_datum_b", gtv(it), gtv("b_datum_$it"), gtv(it % 4 == 0L))
                    }
                }
                .finish()
                .buildGtx()
                .encode()
        )

        buildBlock(DEFAULT_CHAIN_IID, 2, emitDatumsTx)

        val datumRepository = BaseSnapshotDatumRepository(nodes[0].getModules().filterIsInstance<SnapshotAware>(),
                2, cryptoSystem)
        val queryRunner = QueryRunner()

        // Verify data is in place
        withReadConnection(nodes[0].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
            // Assert still the same value at height 0
            val datumA3V1 = datumRepository.getDatumWithType(ctx, 1, 0, 3)
            val datumB19V1 = datumRepository.getDatumWithType(ctx, 1, 1, 19)

            assertThat(datumA3V1).isEqualTo(SnapshotDatum(3, gtv("a_datum_3"), false))
            assertThat(datumB19V1).isEqualTo(SnapshotDatum(19, gtv("b_datum_19"), false))
        }

        // Clear module tables on node 4
        nodes[3].getModules(DEFAULT_CHAIN_IID).filterIsInstance<SnapshotTestModule>().forEach { module ->
            withWriteConnection(nodes[3].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { ctx ->
                val moduleTables = listOf(module.conf.tableName, module.conf.permanentTableName)

                moduleTables.forEach {
                    queryRunner.execute(ctx.conn, "DROP TABLE $it")
                }
                module.initializeDB(ctx)

                moduleTables.forEach {
                    assertThat(queryRunner.query(ctx.conn, "SELECT * FROM $it") { rs -> rs.fetchSize }).isZero()
                }
                true
            }
        }

        // Restore module tables on node 4 from snapshot data on node 1
        val height = Long.MAX_VALUE
        nodes[0].getModules(DEFAULT_CHAIN_IID).filterIsInstance<SnapshotTestModule>().forEach { sourceNodeModule ->
            withReadConnection(nodes[0].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { sourceNodeCtx ->
                val contextId = DatabaseAccess.of(sourceNodeCtx).getSnapshotContextId(sourceNodeCtx, sourceNodeModule::class.java.canonicalName)

                withWriteConnection(nodes[3].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { destinationNodeCtx ->

                    val destinationNodeModule = nodes[3].getModules(DEFAULT_CHAIN_IID).filterIsInstance<SnapshotTestModule>()
                            .find { it.conf.tableName == sourceNodeModule.conf.tableName }!!

                    var offset = 0L
                    while (true) {
                        val datums = datumRepository.getDatums(sourceNodeCtx, height, contextId, offset, maxDataSize, Long.MAX_VALUE,)
                        if (datums.isEmpty()) {
                            break
                        }
                        destinationNodeModule.constructDatum(destinationNodeCtx, datums.mapIndexed { index, datum ->
                            SnapshotDatum(offset + index, datum.data, datum.isPermanent)
                        })
                        offset += datums.size
                    }

                    true
                }
            }
        }

        // Verify identical module tables
        nodes[0].getModules(DEFAULT_CHAIN_IID).filterIsInstance<SnapshotTestModule>().forEach { sourceNodeModule ->
            withReadConnection(nodes[0].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { sourceNodeCtx ->
                withReadConnection(nodes[3].postchainContext.sharedStorage, DEFAULT_CHAIN_IID) { destinationNodeCtx ->
                    listOf(sourceNodeModule.conf.tableName, sourceNodeModule.conf.permanentTableName).forEach {
                        val sql = "SELECT datum_id, datum FROM $it ORDER BY datum_id"
                        val sourceRows = queryRunner.query(sourceNodeCtx.conn, sql, TableStringsHandler())
                        val destinationRows = queryRunner.query(destinationNodeCtx.conn, sql, TableStringsHandler())
                        assertThat(destinationRows).isEqualTo(sourceRows)
                    }
                }
            }
        }
    }
}

class SnapshotTestModuleConf {
    var snapshotContext: SnapshotContext? = null
    var tableName: String? = null
    var permanentTableName: String? = null
}

open class SnapshotTestModule(
    private val moduleName: String,
) : SnapshotAware, SimpleGTXModule<SnapshotTestModuleConf>(SnapshotTestModuleConf(), mapOf(
        "emit_datum_$moduleName" to { conf, opData -> EmitDatumOp(conf, opData) }
), mapOf()) {

    override fun initializeSnapshotContext(context: SnapshotContext) {
        conf.snapshotContext = context
    }

    override fun getPermanentDatumIdMax(ctx: EContext): Long? {
        val sql = "SELECT max(datum_id) FROM ${conf.permanentTableName}"
        val rawDatum = QueryRunner().query(ctx.conn, sql, ScalarHandler<Int>())
        return rawDatum?.toLong()
    }

    override fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv? {
        val sql = "SELECT datum FROM ${conf.permanentTableName} WHERE datum_id = $datumId"
        val rawDatum = QueryRunner().query(ctx.conn, sql, ScalarHandler<ByteArray>())
        if (rawDatum != null) {
            return GtvDecoder.decodeGtv(rawDatum)
        }
        return null
    }

    override fun initializeDB(ctx: EContext) {
        conf.tableName = DatabaseAccess.of(ctx).tableName("snapshot_test_datums_module_$moduleName")
        conf.permanentTableName = DatabaseAccess.of(ctx).tableName("snapshot_test_permanent_datums_module_$moduleName")
        listOf(conf.tableName, conf.permanentTableName).forEach {
            QueryRunner().update(ctx.conn, """
            CREATE TABLE IF NOT EXISTS ${it} (
            datum_id INTEGER PRIMARY KEY,
            datum BYTEA
            )""".trimIndent())
        }
    }

    override fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>) {
        if (datumList.isEmpty()) return

        insertDatumBatch(ctx, datumList.filter { !it.isPermanent }, conf.tableName!!)
        insertDatumBatch(ctx, datumList.filter { it.isPermanent }, conf.permanentTableName!!)
    }

    private fun insertDatumBatch(ctx: EContext, datums: List<SnapshotDatum>, tableName: String) {
        if (datums.isEmpty()) return

        val sql = "INSERT INTO $tableName VALUES (?, ?) ON CONFLICT (datum_id) DO UPDATE SET datum = EXCLUDED.datum"
        ctx.conn.prepareStatement(sql).use { ps ->
            datums.forEach { datum ->
                ps.setLong(1, datum.id)
                ps.setBytes(2, GtvEncoder.encodeGtv(datum.data))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

}

class SnapshotModuleA : SnapshotTestModule("a")

class SnapshotModuleB : SnapshotTestModule("b")

class EmitDatumOp(private val conf: SnapshotTestModuleConf, opData: ExtOpData) : GTXOperation(opData) {

    override fun checkCorrectness() {}

    override fun apply(ctx: TxEContext): Boolean {
        val datumId = data.args[0].asInteger()
        val datum = data.args[1]
        val isPermanent = data.args[2].asBoolean()
        conf.snapshotContext?.emitDatum(ctx, datumId, datum, isPermanent)

        val table = when (isPermanent) {
            true -> conf.permanentTableName
            false -> conf.tableName
        }
        QueryRunner().update(ctx.conn, """
            INSERT INTO ${table} VALUES (?, ?)
            ON CONFLICT (datum_id) DO UPDATE SET datum = EXCLUDED.datum
            """.trimIndent(), datumId, GtvEncoder.encodeGtv(datum))
        return true
    }
}

// Just convert a RS to a column=value string map, used for assert identical table content between nodes
class TableStringsHandler : AbstractListHandler<Map<String, String>>() {
    override fun handleRow(rs: ResultSet): Map<String, String> {
        val columns = rs.metaData.columnCount
        val values = LinkedHashMap<String, String>()
        for (i in 1..columns) {
            values[rs.metaData.getColumnName(i)] = rs.getString(i)
        }
        return values
    }
}