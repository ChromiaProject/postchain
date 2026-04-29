package net.postchain.integrationtest.global_storage_init

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.core.GlobalStorageInitializer
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import java.sql.Connection

/**
 * Test GTX module whose [GlobalStorageInitializer] simulates heavy work (PostgreSQL advisory lock + sleep).
 *
 * Same shape as [HeavyInitModuleA] with separate counters so the test can verify each initializer
 * was invoked independently. Shares the same lock key as A — initializers run sequentially today,
 * so they don't contend, but if execution ever became concurrent, B would block on A's lock.
 */
class HeavyInitModuleB : GTXModule, GlobalStorageInitializer {

    companion object : KLogging() {
        const val LOCK_KEY = HeavyInitModuleA.LOCK_KEY
        const val SLEEP_MS = 2000L

        @Volatile var enabled = false
        @Volatile var callCount = 0
        @Volatile var durationMs = 0L

        fun reset() {
            enabled = false
            callCount = 0
            durationMs = 0L
        }
    }

    override fun initializeGlobalStorage(connection: Connection) {
        if (!enabled) return
        logger.info { "HeavyInitModuleB: starting heavy global init (lock=$LOCK_KEY, sleep=${SLEEP_MS}ms)" }
        val start = System.currentTimeMillis()
        try {
            connection.createStatement().use { it.execute("SELECT pg_advisory_lock($LOCK_KEY)") }
            logger.info { "HeavyInitModuleB: acquired advisory lock $LOCK_KEY" }
            Thread.sleep(SLEEP_MS)
        } finally {
            connection.createStatement().use { it.execute("SELECT pg_advisory_unlock($LOCK_KEY)") }
            durationMs = System.currentTimeMillis() - start
            callCount++
            logger.info { "HeavyInitModuleB: released lock $LOCK_KEY, completed in ${durationMs}ms" }
        }
    }

    override fun makeTransactor(opData: ExtOpData): Transactor = throw NotImplementedError()
    override fun getOperations(): Set<String> = emptySet()
    override fun getQueries(): Set<String> = emptySet()
    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv = throw NotImplementedError()
    override fun initializeDB(ctx: EContext) {}
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = emptyList()
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = emptyList()
}
