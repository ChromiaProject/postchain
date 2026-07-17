package net.postchain.base.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.StorageBuilder
import net.postchain.base.TestBlockQueries
import net.postchain.base.TestBlockchainBuilder
import net.postchain.base.withReadConnection
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Regression test for the same-thread config-migration deadlock
 * ("Reuse held write connection for same-chain block queries").
 *
 * Scenario: during the first block after a schema-migrating configuration update, the
 * chain-start/build thread holds an open write transaction with ACCESS EXCLUSIVE locks on the
 * chain's tables (Rell's SqlInit column drops). A same-chain [net.postchain.base.BaseBlockQueries]
 * query issued from that thread must reuse the held write context ([
 * net.postchain.core.Storage.getExistingWriteContext]) — opening a second connection would block
 * on the transaction's own locks: a permanent single-thread deadlock that Postgres cannot detect
 * (the write connection is idle-in-transaction, not lock-waiting) and no timeout can rescue
 * (a cancelled query would just be retried into the same wall).
 *
 * This cannot be covered deterministically at the deployment-test level: in postchain-chromia the
 * ICMF receiver's pipe creation itself reads the migration-locked tables asynchronously, so the
 * migration block never observes the pipes (see Directory1DeadlockIT history in postchain-chromia).
 */
class BlockQueriesWriteContextReuseTest {

    private val appConfig = testDbConfig("write_ctx_reuse", 1)
    private val configData0 = GtvMLParser.parseGtvML(
            javaClass.getResource("../importexport/blockchain_configuration_0.xml")!!.readText())

    @Test
    fun `same-thread block query reuses the held write connection instead of deadlocking on its own locks`() {
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            val builder = TestBlockchainBuilder(storage, configData0)
            builder.buildBlockchain(listOf(0L to configData0), 3)
            val chainId = builder.chainId
            val queries = TestBlockQueries(
                    storage, BaseBlockStore(), chainId, "".toByteArray(), builder.hashCalculator, mock())

            val expectedHeight = queries.getLastBlockHeight().toCompletableFuture().get(10, TimeUnit.SECONDS)

            // Simulate the config-migration transaction: an open write context holding an
            // ACCESS EXCLUSIVE lock on a chain table, exactly like SqlInit's ALTER TABLE.
            val writeCtx = storage.openWriteConnection(chainId)
            try {
                writeCtx.conn.createStatement().use {
                    it.execute("""LOCK TABLE "c$chainId.blocks" IN ACCESS EXCLUSIVE MODE""")
                }

                // Sanity: the lock genuinely blocks readers on OTHER connections.
                val otherConnectionBlocks = CompletableFuture.supplyAsync {
                    withReadConnection(storage, chainId) { ctx ->
                        try {
                            ctx.conn.createStatement().use { st ->
                                st.execute("SET LOCAL statement_timeout = 1000")
                                st.executeQuery("""SELECT COUNT(*) FROM "c$chainId.blocks"""").close()
                            }
                            false
                        } catch (_: SQLException) {
                            ctx.conn.rollback() // leave the aborted tx cleanly before the pool reclaim
                            true
                        }
                    }
                }
                assertThat(otherConnectionBlocks.get(10, TimeUnit.SECONDS)).isTrue()

                // The fix under test: this thread holds the write context for the chain, so the
                // same-chain query must run inside the held transaction and complete promptly.
                // On pre-fix code this call blocks on the lock (until the 60 s query timeout
                // cancels the statement and the stage completes exceptionally) — failing the test.
                val height = queries.getLastBlockHeight().toCompletableFuture().get(10, TimeUnit.SECONDS)
                assertThat(height).isEqualTo(expectedHeight)

                // The reused write context must survive the query: still open, still committable.
                assertThat(writeCtx.conn.isClosed).isFalse()
            } finally {
                storage.closeWriteConnection(writeCtx, true)
            }

            // After the "migration" commits, normal read-path queries work again.
            assertThat(queries.getLastBlockHeight().toCompletableFuture().get(10, TimeUnit.SECONDS))
                    .isEqualTo(expectedHeight)
        }
    }
}
