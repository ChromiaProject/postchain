package net.postchain.integrationtest.reconfiguration

import assertk.assert
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import mu.KLogging
import net.postchain.base.SpecialTransactionPosition
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.SQLDatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.getModules
import net.postchain.gtx.GTXModule
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class FaultyConfigTest : IntegrationTestSetup() {

    @BeforeEach
    fun setup() {
        SimulateFaultyConfigSpecialTxExtension.hasFailed = false
    }

    @Test
    fun `config which fails before block building is reverted`() {
        val (node) = createNodes(1, "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_initial_1.xml")

        val invalidConfig = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_invalid_1.xml"
        )
        node.addConfiguration(DEFAULT_CHAIN_IID, 2, invalidConfig)
        withReadConnection(node.postchainContext.storage, DEFAULT_CHAIN_IID) { ctx ->
            val db = DatabaseAccess.of(ctx)
            assert(db.getConfigurationData(ctx, 2)).isNotNull()
        }

        buildBlock(DEFAULT_CHAIN_IID, 1)
        Thread.sleep(1 * 1000)
        buildBlock(DEFAULT_CHAIN_IID, 3)

        // Assert that DB schema change by faulty config is rolled back
        node.postchainContext.storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assert(db.tableExists(ctx.conn, "should_fail")).isFalse()
        }
        // Assert that the invalid configuration was removed from DB
        withReadConnection(node.postchainContext.storage, DEFAULT_CHAIN_IID) { ctx ->
            val db = DatabaseAccess.of(ctx)
            assert(db.getConfigurationData(ctx, 2)).isNull()
        }
    }

    @Test
    fun `config which fails during block building is not persisted`() {
        val (node) = createNodes(1, "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_initial_1.xml")

        val faultyConfig = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_faulty_1.xml"
        )
        node.addConfiguration(DEFAULT_CHAIN_IID, 2, faultyConfig)
        buildBlock(DEFAULT_CHAIN_IID, 1)

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assert(node.getModules(DEFAULT_CHAIN_IID).any { it is FaultyGTXModule })
        }

        buildBlockNoWait(listOf(node), DEFAULT_CHAIN_IID, 2)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assert(SimulateFaultyConfigSpecialTxExtension.hasFailed).isTrue()
        }

        // Assert that DB schema change by faulty config is not persisted
        node.postchainContext.storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assert(db.tableExists(ctx.conn, "should_fail")).isFalse()
        }

        val correctConfig = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_correct_1.xml"
        )
        node.addConfiguration(DEFAULT_CHAIN_IID, 2, correctConfig)
        node.stopBlockchain(DEFAULT_CHAIN_IID)
        node.startBlockchain(DEFAULT_CHAIN_IID)

        buildBlock(DEFAULT_CHAIN_IID, 3)
        assert(SimulateFaultyConfigSpecialTxExtension.hasFailed).isFalse()

        // Assert that DB schema change by correct config is persisted
        node.postchainContext.storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assert(db.tableExists(ctx.conn, "should_fail")).isTrue()
        }
    }

    @Test
    fun `config which fails during block building is reverted`() {
        val (node) = createNodes(1, "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_initial_1.xml")

        val faultyConfig = readBlockchainConfig(
                "/net/postchain/devtools/reconfiguration/single_peer/faulty/blockchain_config_faulty_1.xml"
        )
        node.addConfiguration(DEFAULT_CHAIN_IID, 2, faultyConfig)
        buildBlock(DEFAULT_CHAIN_IID, 1)

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assert(node.getModules(DEFAULT_CHAIN_IID).any { it is FaultyGTXModule })
        }
        buildBlockNoWait(listOf(node), DEFAULT_CHAIN_IID, 2)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assert(SimulateFaultyConfigSpecialTxExtension.hasFailed).isTrue()
        }

        buildBlock(DEFAULT_CHAIN_IID, 3)
        // Assert that DB schema change by faulty config is rolled back
        node.postchainContext.storage.withReadConnection { ctx ->
            val db = DatabaseAccess.of(ctx) as SQLDatabaseAccess
            assert(db.tableExists(ctx.conn, "should_fail")).isFalse()
        }
        // Assert that the invalid configuration was removed from DB
        withReadConnection(node.postchainContext.storage, DEFAULT_CHAIN_IID) { ctx ->
            val db = DatabaseAccess.of(ctx)
            assert(db.getConfigurationData(ctx, 2)).isNull()
        }
    }
}

open class FaultyGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {
    open val shouldFail = true
    private val queryRunner = QueryRunner()
    private val specialTxExtension = SimulateFaultyConfigSpecialTxExtension

    override fun initializeDB(ctx: EContext) {
        queryRunner.update(ctx.conn, "CREATE TABLE IF NOT EXISTS should_fail (id BIGINT PRIMARY KEY, fail BOOLEAN)")
        // If faulty config DB changes were persisted this value can't be updated, in that case we can't recover
        queryRunner.update(ctx.conn, "INSERT INTO should_fail (id,fail) VALUES (1,$shouldFail) ON CONFLICT DO NOTHING")
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(specialTxExtension)
    }
}

class CorrectConfigGTXModule : FaultyGTXModule() {
    override val shouldFail = false
}

object SimulateFaultyConfigSpecialTxExtension : GTXSpecialTxExtension, KLogging() {
    @Volatile
    var hasFailed = false
    private val queryRunner = QueryRunner()
    private val boolRes = ScalarHandler<Boolean>()

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {}

    override fun getRelevantOps() = setOf<String>()

    override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = when (position) {
        SpecialTransactionPosition.Begin -> true
        SpecialTransactionPosition.End -> false
    }

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        checkIfWeShouldFail(bctx)
        return listOf()
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>) = true

    private fun checkIfWeShouldFail(bctx: BlockEContext) {
        val shouldFail = queryRunner.query(bctx.conn, "SELECT fail FROM should_fail WHERE id = 1", boolRes)
        logger.info("Should fail: $shouldFail")
        hasFailed = shouldFail
        if (shouldFail) {
            throw UserMistake("You wanted me to fail")
        }
    }
}
