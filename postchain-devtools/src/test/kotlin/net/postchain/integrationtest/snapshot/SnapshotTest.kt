package net.postchain.integrationtest.snapshot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.isContentEqualTo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.base.withReadConnection
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
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SnapshotTest : IntegrationTestSetup() {

    @Test
    fun snapshotTest() {
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
        val datumRepository = SnapshotDatumRepository(nodes[0].getModules().filterIsInstance<SnapshotAware>())

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
}

class SnapshotTestModuleConf() {
    var snapshotContext: SnapshotContext? = null
    var tableName: String? = null
}

open class SnapshotTestModule(
    private val moduleName: String,
) : SnapshotAware, SimpleGTXModule<SnapshotTestModuleConf>(SnapshotTestModuleConf(), mapOf(
        "emit_datum_$moduleName" to { conf, opData -> EmitDatumOp(conf, opData) }
), mapOf()) {

    override fun initializeSnapshotContext(context: SnapshotContext) {
        conf.snapshotContext = context
    }

    override fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv {
        val sql = "SELECT datum FROM ${conf.tableName} WHERE datum_id = $datumId"
        val rawDatum = QueryRunner().query(ctx.conn, sql, ScalarHandler<ByteArray>())
        return GtvDecoder.decodeGtv(rawDatum)
    }

    override fun initializeDB(ctx: EContext) {
        conf.tableName = DatabaseAccess.of(ctx).tableName("snapshot_test_datums_module_$moduleName")
        val sql = "CREATE TABLE IF NOT EXISTS ${conf.tableName} (" +
                "datum_id INTEGER PRIMARY KEY," +
                " datum BYTEA" +
                ")"
        QueryRunner().update(ctx.conn, sql)
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

        if (isPermanent) {
            val updateSql = "INSERT INTO ${conf.tableName} VALUES (?, ?)"
            QueryRunner().update(ctx.conn, updateSql, datumId, GtvEncoder.encodeGtv(datum))
        }
        return true
    }
}
