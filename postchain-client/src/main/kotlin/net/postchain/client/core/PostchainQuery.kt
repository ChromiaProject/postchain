package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

/**
 * Query a Postchain blockchain.
 */
interface PostchainQuery {
/**
 * Perform a query
 * @param name name of the query
 * @param gtv query arguments
 * [Note] Arguments must be provided as a GtvDictionary
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
