package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary

/**
 * Query a Postchain blockchain.
 */
interface PostchainQuery {
    /**
     * Perform a query
     */
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv
}
