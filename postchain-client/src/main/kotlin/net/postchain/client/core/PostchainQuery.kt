package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

/**
 * Query a Postchain blockchain.
 */
fun interface PostchainQuery {
    /**
     * Perform a query.
     *
     * @param name name of the query
     * @param gtv query arguments, must be provided as a [GtvDictionary]
     * @return query result
     */
    fun query(name: String, gtv: Gtv): Gtv
}
