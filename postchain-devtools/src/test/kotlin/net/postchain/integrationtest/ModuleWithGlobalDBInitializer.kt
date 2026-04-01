package net.postchain.integrationtest

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
 * Test GTX module that also implements [GlobalStorageInitializer].
 *
 * Two separate counters let tests verify the distinction:
 * - [globalInitCallCount]: incremented by [initializeGlobalStorage] (node startup, committed before any chain)
 * - [chainInitCallCount]: incremented by [initializeDB] (per-blockchain startup)
 *
 * State is held in the companion object because both ServiceLoader and GTXBlockchainConfigurationFactory
 * construct fresh instances independently. Call [reset] in @BeforeEach to isolate tests.
 */
class ModuleWithGlobalDBInitializer : GTXModule, GlobalStorageInitializer {

    companion object {
        @Volatile var globalInitCallCount = 0
        @Volatile var chainInitCallCount = 0
        @Volatile var connectionWasOpen = false

        fun reset() {
            globalInitCallCount = 0
            chainInitCallCount = 0
            connectionWasOpen = false
        }
    }

    // GlobalStorageInitializer — called once at node startup via ServiceLoader
    override fun initializeGlobalStorage(connection: Connection) {
        globalInitCallCount++
        connectionWasOpen = !connection.isClosed
    }

    // GTXModule — called per blockchain startup
    override fun initializeDB(ctx: EContext) {
        chainInitCallCount++
    }

    override fun makeTransactor(opData: ExtOpData): Transactor = throw NotImplementedError()
    override fun getOperations(): Set<String> = emptySet()
    override fun getQueries(): Set<String> = emptySet()
    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv = throw NotImplementedError()
    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = emptyList()
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = emptyList()
}
