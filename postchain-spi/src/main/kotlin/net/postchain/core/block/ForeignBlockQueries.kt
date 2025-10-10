package net.postchain.core.block

import net.postchain.gtv.Gtv
import java.time.Duration
import java.util.concurrent.CompletionStage

class ForeignBlockQueries(
        private val blockQueries: BlockQueries
) : BlockQueries by blockQueries {

    companion object {
        val FOREIGN_CHAIN_TABLE_LOCK_TIMEOUT_MS = Duration.ofMillis(100)
        val FOREIGN_CHAIN_TABLE_QUERY_TIMEOUT_MS = Duration.ofMillis(10_000)
    }

    override fun query(name: String, args: Gtv): CompletionStage<Gtv> =
            blockQueries.queryWithTimeout(name, args, FOREIGN_CHAIN_TABLE_QUERY_TIMEOUT_MS, FOREIGN_CHAIN_TABLE_LOCK_TIMEOUT_MS)
}