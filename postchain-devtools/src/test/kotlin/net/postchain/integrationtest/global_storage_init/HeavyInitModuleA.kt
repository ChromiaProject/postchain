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
 * Opt-in via [enabled]. ServiceLoader registers this for every node startup, so we keep it a no-op by
 * default to avoid slowing down unrelated tests.
 *
 * Uses lock key shared with [HeavyInitModuleB] — would contend if initializers ever ran concurrently.
 */
class HeavyInitModuleA : GTXModule, GlobalStorageInitializer {

    companion object : KLogging() {
        const val LOCK_KEY = 1000
        const val SLEEP_MS = 1000L

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
        logger.info { "HeavyInitModuleA: starting heavy global init (lock=$LOCK_KEY, sleep=${SLEEP_MS}ms)" }
        val start = System.currentTimeMillis()
        try {
            connection.createStatement().use { it.execute("SELECT pg_advisory_lock($LOCK_KEY)") }
            logger.info { "HeavyInitModuleA: acquired advisory lock $LOCK_KEY" }
            Thread.sleep(SLEEP_MS)
        } finally {
            connection.createStatement().use { it.execute("SELECT pg_advisory_unlock($LOCK_KEY)") }
            durationMs = System.currentTimeMillis() - start
            callCount++
            logger.info { "HeavyInitModuleA: released lock $LOCK_KEY, completed in ${durationMs}ms" }
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
