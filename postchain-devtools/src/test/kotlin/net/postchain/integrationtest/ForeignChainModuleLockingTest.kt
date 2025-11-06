package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.PostchainContext
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.crypto.CryptoSystem
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.getModules
import net.postchain.devtools.utils.ChainUtil
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtx.GTXBlockchainConfigurationFactory
import net.postchain.gtx.GTXModule
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException

class ForeignChainModuleLockingTest : ManagedModeTest() {

    @Test
    fun `Assert foreign query times out on DB lock`() {
        startManagedSystem(1, 0)

        val foreignChainConfig = GtvMLParser.parseGtvML(Any::class::class.java.getResource("/net/postchain/devtools/foreign_chain_lock/blockchain_module_foreign.xml")!!.readText())
        val foreignChain = startNewBlockchain(setOf(0), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(foreignChainConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(foreignChain)

        val foreignBrid = ChainUtil.ridOf(foreignChain)
        val foreignAccessChainConfig = GtvMLParser.parseGtvML(
                Any::class::class.java.getResource("/net/postchain/devtools/foreign_chain_lock/blockchain_module_foreign_access.xml")!!.readText()
                        .replace("<string>FOREIGN_BRID</string>", "<bytea>${foreignBrid.toHex()}</bytea>")
        )
        val foreignAccessChain = startNewBlockchain(setOf(0), setOf(), null, rawBlockchainConfiguration = GtvEncoder.encodeGtv(foreignAccessChainConfig), blockchainConfigurationFactory = GTXBlockchainConfigurationFactory())

        buildBlock(foreignAccessChain) // See that we can build blocks
        assertThat(nodes[0].getModules(foreignAccessChain)
                .filterIsInstance<ForeignAccessGTXModule>()
                .first().specialTxExtension.gotLockTimeout
        ).isFalse()

        // Restart foreign chain to trigger lock
        nodes[0].stopBlockchain(foreignChain)
        nodes[0].startBlockchain(foreignChain)

        // Ensure we can still build blocks on foreign access chain
        buildBlock(foreignAccessChain)
        assertThat(nodes[0].getModules(foreignAccessChain)
                .filterIsInstance<ForeignAccessGTXModule>()
                .first().specialTxExtension.gotLockTimeout
        ).isTrue()
    }
}

class ForeignAccessGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()), PostchainContextAware {
    val specialTxExtension = ForeignAccessSpecialTxExtension()

    override fun initializeDB(ctx: EContext) {}

    override fun getSpecialTxExtensions() = listOf(specialTxExtension)

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        specialTxExtension.foreignBrid = BlockchainRid(configuration.rawConfig["foreign_brid"]!!.asByteArray())
        specialTxExtension.blockQueriesProvider = postchainContext.blockQueriesProvider
    }
}

class ForeignAccessSpecialTxExtension() : GTXSpecialTxExtension {
    lateinit var blockQueriesProvider: BlockQueriesProvider
    lateinit var foreignBrid: BlockchainRid
    var gotLockTimeout = false

    override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {}

    override fun getRelevantOps(): Set<String> = setOf()

    override fun needsSpecialTransaction(position: SpecialTransactionPosition) =
            position == SpecialTransactionPosition.Begin

    override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
        if (!::blockQueriesProvider.isInitialized) return listOf()

        try {
            // Just do the query, we don't care about the result
            blockQueriesProvider.getBlockQueries(foreignBrid)!!
                    .query("locking_dummy_test_query", gtv(mapOf())).get()
        } catch (e: PSQLException) {
            if ((e.cause as? PSQLException)?.sqlState == "55P03") gotLockTimeout = true
            return listOf()
        }

        return listOf()
    }

    override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>) = true

}

class ForeignLockingGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf(
        "locking_dummy_test_query" to { _, ctx, _ ->
            QueryRunner().query(ctx.conn, "SELECT id FROM locking_dummy_test", ScalarHandler<Long?>())?.let {
                gtv(it)
            } ?: GtvNull
        }
)) {
    private val queryRunner = QueryRunner()

    override fun initializeDB(ctx: EContext) {
        queryRunner.update(ctx.conn, "CREATE TABLE IF NOT EXISTS locking_dummy_test (id BIGINT PRIMARY KEY)")

        // Pretend we had some migration here
        queryRunner.update(ctx.conn, "ALTER TABLE locking_dummy_test ADD COLUMN IF NOT EXISTS created_at TIMESTAMP")
    }
}